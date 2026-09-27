"""HTTP integration tests against the actual Java API using isolated temporary data."""
import http.cookiejar
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import urllib.request
import urllib.error

ROOT = Path(__file__).resolve().parents[1]
CLASSES = str(Path(sys.argv[1] if len(sys.argv) > 1 else ROOT / 'bin').resolve())

class Client:
    def __init__(self):
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.state = None

    def request(self, path, body=None, expected=200, origin=None, authorization=None, workspace=None):
        headers = {'Content-Type': 'application/json'}
        if workspace is not None:
            headers['X-Workspace-Tenant'] = workspace
        if authorization:
            headers['Authorization'] = authorization
        if origin:
            headers['Origin'] = origin
        req = urllib.request.Request('http://localhost:8080/api/platform/' + path,
                                     data=json.dumps(body).encode() if body is not None else None, headers=headers)
        try:
            response = self.opener.open(req, timeout=10)
        except urllib.error.HTTPError as exc:
            response = exc
        data = json.load(response)
        assert response.status == expected, (path, response.status, data, expected)
        if response.status == 200 and 'revision' in data:
            self.state = data
        return data

    def login(self, username):
        return self.request('login', {'username': username, 'password': 'DemoPass123!'})

    def command(self, action, expected=200, **kwargs):
        self.request('state')
        return self.request('command', {'action': action, 'revision': self.state['revision'], **kwargs}, expected)

with tempfile.TemporaryDirectory(prefix='parking-platform-tests-') as temporary:
    (Path(temporary) / 'web').symlink_to(ROOT / 'web', target_is_directory=True)
    env = dict(os.environ, PLATFORM_DEMO='true', PLATFORM_ONLY='true', PLATFORM_DEVICE_GATEWAY='true', PLATFORM_DATA_DIR=str(Path(temporary) / 'platform'))
    def start():
        process = subprocess.Popen(['java', '-cp', CLASSES, 'server.ParkingServer'], cwd=temporary, env=env,
                                   stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        for _ in range(100):
            if process.poll() is not None:
                raise AssertionError(process.stderr.read().decode())
            try:
                Client().request('state', expected=401)
                return process
            except (OSError, urllib.error.URLError):
                time.sleep(.1)
        process.terminate()
        raise AssertionError('Server failed to start')
    process = start()
    try:
        with urllib.request.urlopen('http://localhost:8080/') as page:
            assert page.url.endswith('/dashboard.html')
            assert b'js/dashboard.js' in page.read()
        with urllib.request.urlopen('http://localhost:8080/platform.html') as page:
            assert b'js/platform.js' in page.read()
        try:
            urllib.request.urlopen('http://localhost:8080/js/app.js')
            raise AssertionError('Legacy demo API client exposed in production')
        except urllib.error.HTTPError as exc:
            assert exc.code == 404
        owner, other, admin, staff, superuser = [Client() for _ in range(5)]
        owner.login('owner'); other.login('owner2'); admin.login('admin'); staff.login('staff'); superuser.login('superadmin')
        assert len(owner.state['sites']) == 3
        assert len(other.state['sites']) == 1
        assert len(staff.state['sites']) == 1
        # Workspace selectors never expand a customer's server-side permissions.
        for client in [owner, other, admin, staff]:
            own=client.state['user']['tenantId']
            foreign='DEMO-B' if own=='DEMO-A' else 'DEMO-A'
            client.request('state', expected=403, workspace=foreign)
            scoped=client.request('state', workspace=own)
            assert all(t['id']==own for t in scoped['tenants'])
            for key in ['sites','users','audit']:
                assert all(row['tenantId']==own for row in scoped[key]), key
            assert all(u['role']!='super_admin' for u in scoped['users'])
        scoped=superuser.request('state',workspace='DEMO-A')
        assert len(scoped['tenants'])==1 and len(scoped['sites'])==3
        assert all(u['tenantId']=='DEMO-A' for u in scoped['users'])
        superuser.request('state')
        foreign_user=other.state['user']['id']
        owner.command('setUserActive',expected=403,userId=foreign_user,active=False)
        owner.command('revokeSessions',expected=403,userId=foreign_user)
        site = owner.state['sites'][0]
        sid = site['id']
        assert 'hash' not in json.dumps(owner.state) and 'salt' not in json.dumps(owner.state)
        other.command('configure', expected=403, siteId=sid)
        for action in ['checkin','checkout','saveLayout','addMember','reserve','addDevice']:
            other.command(action,expected=403,siteId=sid)
        owner.command('createUser',expected=403,username='escalate',password='LongPassword123!',role='super_admin',siteIds=[sid])
        foreign_site=other.state['sites'][0]['id']
        owner.command('createUser',expected=403,username='crosssite',password='LongPassword123!',role='staff',siteIds=[foreign_site])
        staff.command('saveLayout', expected=403, siteId=sid, cells=site['draft'])
        admin.command('publish', expected=403, siteId=sid)
        owner.command('publish', siteId=sid)
        owner.command('createTenant', expected=403, name='forbidden')
        staff.command('checkin', siteId=sid, plate='TEST-01', slotId='A4', vehicleType='CAR')
        ticket = next(t for t in staff.state['sites'][0]['tickets'] if t['licensePlate'] == 'TEST-01')
        owner.command('checkin', expected=400, siteId=sid, plate='TEST-01', slotId='A6', vehicleType='CAR')
        owner.command('checkin', expected=400, siteId=sid, plate='MOTO', slotId='A6', vehicleType='MOTORCYCLE')
        cells = [c for c in site['draft'] if c['id'] != 'A4']
        owner.command('saveLayout', siteId=sid, cells=cells)
        owner.command('publish', expected=400, siteId=sid)
        staff.command('checkout', siteId=sid, ticketId=ticket['ticketId'])
        assert all(t['status']=='ACTIVE' for t in staff.state['sites'][0]['tickets'])
        staff.command('checkout', expected=400, siteId=sid, ticketId=ticket['ticketId'])
        owner.command('publish', siteId=sid)
        owner.command('saveLayout', siteId=sid, cells=[c for c in cells if c['id'] != 'R1'])
        owner.command('publish', expected=400, siteId=sid)
        owner.command('saveLayout', siteId=sid, cells=cells)
        version = owner.state['sites'][0]['versions'][0]['id']
        owner.command('restore', siteId=sid, versionId=version)
        owner.command('publish', siteId=sid)
        owner.command('createSite', name='Custom test', businessType='CUSTOM')
        custom=owner.state['sites'][-1]
        owner.command('publish', expected=400, siteId=custom['id'])
        owner.command('configure', siteId=sid, name='Configured', address='Bangkok', rate=35, freeMinutes=10,
                      active=True, features={'membership':True,'reservation':True})
        owner.command('addMember', siteId=sid, plate='MEMBER', name='Test member', room='101', expires='2030-12-01')
        owner.command('reserve', siteId=sid, slotId='A6', plate='BOOKED', **{'from':'2030-01-01T10:00:00Z','to':'2030-01-01T12:00:00Z'})
        owner.command('reserve', expected=400, siteId=sid, slotId='A6', plate='OVERLAP', **{'from':'2030-01-01T11:00:00Z','to':'2030-01-01T13:00:00Z'})
        owner.command('checkin', expected=400, siteId=sid, slotId='A6', plate='OTHER', vehicleType='CAR')
        owner.command('addDevice', siteId=sid, name='Entry camera', type='CAMERA')
        owner.command('createUser', username='newstaff', password='LongPassword123!', role='staff', siteIds=[sid])
        team_id=next(u['id'] for u in owner.state['users'] if u['username']=='newstaff')
        employee=Client(); employee.request('login',{'username':'newstaff','password':'LongPassword123!'})
        assert [s['id'] for s in employee.state['sites']]==[sid]
        assert employee.state['users']==[]
        employee.command('updateTeamUser',expected=403,userId=team_id,role='admin',siteIds=[sid])
        other.command('resetTeamPassword',expected=403,userId=team_id,newPassword='ResetPassword123!')
        owner.command('updateTeamUser',expected=403,userId=team_id,role='owner',siteIds=[sid])
        owner.command('updateTeamUser',expected=403,userId=team_id,role='staff',siteIds=[foreign_site])
        owner.command('updateTeamUser',expected=400,userId=team_id,role='staff',siteIds=[])
        owner.command('updateTeamUser',userId=team_id,role='staff',siteIds=[custom['id']])
        employee.request('state',expected=401)
        employee.request('login',{'username':'newstaff','password':'LongPassword123!'})
        assert [s['id'] for s in employee.state['sites']]==[custom['id']]
        employee.command('checkin',expected=403,siteId=sid,plate='FORBIDDEN',slotId='A4',vehicleType='CAR')
        owner.command('resetTeamPassword',userId=team_id,newPassword='ResetPassword123!')
        employee.request('state',expected=401)
        employee.request('login',{'username':'newstaff','password':'LongPassword123!'},expected=403)
        employee.request('login',{'username':'newstaff','password':'ResetPassword123!'})
        assert 'ResetPassword123!' not in json.dumps(owner.state)
        assert owner.state['user']['role']=='owner'
        superuser.command('createTenant', name='New customer', username='customer', password='LongPassword123!')
        assert len(superuser.state['tenants']) == 3
        presentation_owner=Client(); presentation_owner.request('login',{'username':'customer','password':'LongPassword123!'})
        presentation_owner.command('createPresentation')
        demo=next(s for s in presentation_owner.state['sites'] if s.get('presentation'))
        demo_id=demo['id']
        assert {'STANDARD','EV_CHARGING','MOTORCYCLE','VIP','ACCESSIBLE','LARGE'} <= {c['slotType'] for c in demo['published'] if c['type']=='SLOT'}
        assert len(demo['tickets'])==30
        presentation_owner.command('createPresentation')
        assert len(presentation_owner.state['sites'])==1
        staff.command('createPresentation',expected=403)
        other.command('resetPresentation',expected=403,siteId=demo_id,confirmed=True)
        owner.command('resetPresentation',expected=403,siteId=sid,confirmed=True)
        presentation_owner.command('checkin',siteId=demo_id,plate='DEMO-TRUCK',vehicleType='TRUCK',slotId='A20')
        presentation_owner.command('sampleTime',siteId=demo_id,minutes=60)
        presentation_owner.command('resetPresentation',siteId=demo_id,confirmed=True)
        reset=next(s for s in presentation_owner.state['sites'] if s['id']==demo_id)
        assert reset['simulationMinutes']==0 and all(t['status']=='EXITED' for t in reset['tickets'])

        Client().request('command', {'action':'publish'}, expected=401)
        owner.request('command', {'action':'publish','siteId':sid,'revision':-1}, expected=400)
        owner.request('command', {'action':'publish','siteId':sid,'revision':0}, expected=409)
        owner.request('command', {}, expected=403, origin='https://attacker.example')
        owner.request('state'); assert owner.state['audit']
        assert all(s['tenantId']=='DEMO-A' for s in owner.state['sites'])
        process.terminate(); process.wait(timeout=5)
        process=start()
        owner=Client(); owner.login('owner')
        assert owner.state['sites'][0]['name']=='Configured'
        assert owner.state['sites'][0]['memberships'][0]['room']=='101'
        assert len(owner.state['sites'])==4
        # Account changes preserve business data and invalidate existing sessions.
        staff=Client(); staff.login('staff')
        policy={k:-1 for k in ['carRate','evRate','motorcycleRate','truckRate','weekendRate','holidayRate']}
        policy.update(dailyCap=100,memberDiscountPercent=50,roomQuota=1,requireVisitorApproval=True,holidays=[])
        staff.command('configurePolicy',expected=403,siteId=sid,policy=policy)
        owner.command('configurePolicy',siteId=sid,policy=policy)
        from datetime import datetime, timedelta, timezone
        expires=(datetime.now(timezone.utc)+timedelta(hours=1)).isoformat().replace('+00:00','Z')
        staff.command('requestVisitor',siteId=sid,plate='VISITOR',room='201',expires=expires)
        staff.command('checkin',expected=400,siteId=sid,plate='VISITOR',slotId='A4',vehicleType='CAR')
        owner.request('state')
        visitor=next(s for s in owner.state['sites'] if s['id']==sid)['visitors'][-1]
        staff.command('approveVisitor',expected=403,siteId=sid,visitorId=visitor['id'],approved=True)
        owner.command('approveVisitor',siteId=sid,visitorId=visitor['id'],approved=True)
        staff.command('checkin',siteId=sid,plate='VISITOR',slotId='A4',vehicleType='CAR')
        owner.request('state')
        mall=next(s for s in owner.state['sites'] if s['businessType']=='MALL')
        hotel=next(s for s in owner.state['sites'] if s['businessType']=='HOTEL')
        owner.command('checkin',siteId=mall['id'],plate='COUPON',slotId='A4',vehicleType='CAR')
        ticket=next(s for s in owner.state['sites'] if s['id']==mall['id'])['tickets'][-1]
        owner.command('applyCoupon',siteId=mall['id'],ticketId=ticket['ticketId'],code='RECEIPT-1',percent=25)
        owner.command('applyCoupon',expected=400,siteId=mall['id'],ticketId=ticket['ticketId'],code='RECEIPT-1',percent=25)
        owner.command('checkin',siteId=hotel['id'],plate='VALET',slotId='A4',vehicleType='CAR')
        ticket=next(s for s in owner.state['sites'] if s['id']==hotel['id'])['tickets'][-1]
        owner.command('valet',siteId=hotel['id'],ticketId=ticket['ticketId'],stage='RECEIVED')
        owner.command('checkout',expected=400,siteId=hotel['id'],ticketId=ticket['ticketId'])
        for stage in ['PARKED','RETURNED']:owner.command('valet',siteId=hotel['id'],ticketId=ticket['ticketId'],stage=stage)
        owner.command('checkout',siteId=hotel['id'],ticketId=ticket['ticketId'])
        owner.command('addRoadCurve',siteId=sid,label='Curve',floor=1,width=6,x1=10,y1=10,cx=80,cy=10,x2=80,y2=80)
        owner.command('addRoadCurve',expected=400,siteId=sid,label='Invalid',floor=1,width=6,x1=-1,y1=10,cx=80,cy=10,x2=80,y2=80)
        owner.command('addDevice',siteId=sid,name='Bench',type='ESP32')
        device=next(s for s in owner.state['sites'] if s['id']==sid)['devices'][-1]
        staff.command('provisionDevice',expected=403,siteId=sid,deviceId=device['id'])
        owner.command('provisionDevice',siteId=sid,deviceId=device['id'])
        device_token=owner.state['deviceSecret']
        event={'siteId':sid,'deviceId':device['id'],'type':'HEARTBEAT'}
        Client().request('device',event,expected=403)
        Client().request('device',event,authorization='Bearer '+device_token)
        owner.request('state')
        assert 'deviceSecret' not in owner.state and 'tokenHash' not in json.dumps(owner.state)
        owner.command('queueLed',siteId=sid,deviceId=device['id'])
        response=Client().request('device',event,authorization='Bearer '+device_token)
        ack={**event,'type':'ACK','commandId':response['command']['id']}
        Client().request('device',ack,authorization='Bearer '+device_token)
        Client().request('device',ack,authorization='Bearer '+device_token)
        owner.command('disableDevice',siteId=sid,deviceId=device['id'])
        Client().request('device',event,expected=403,authorization='Bearer '+device_token)
        owner.request('state')
        staff_id=next(u['id'] for u in owner.state['users'] if u['username']=='staff')
        other=Client(); other.login('owner2')
        other.command('setUserActive',expected=403,userId=staff_id,active=False)
        owner.command('setUserActive',userId=staff_id,active=False)
        staff.request('state',expected=401)
        Client().request('login',{'username':'staff','password':'DemoPass123!'},expected=403)
        owner.command('setUserActive',userId=staff_id,active=True)
        staff=Client(); staff.login('staff')
        owner.command('revokeSessions',userId=staff_id)
        staff.request('state',expected=401)
        owner.command('setUserActive',expected=403,userId=owner.state['user']['id'],active=False)
        owner.command('changePassword',expected=403,currentPassword='WrongPassword123!',newPassword='ChangedPassword123!')
        owner.command('changePassword',currentPassword='DemoPass123!',newPassword='ChangedPassword123!')
        owner.request('state',expected=401)
        owner.request('login',{'username':'owner','password':'ChangedPassword123!'})
        assert len(owner.state['sites'])==4
        assert 'ChangedPassword123!' not in json.dumps(owner.state)
        owner.command('createSampleWorkspace', expected=403)
        superuser=Client(); superuser.login('superadmin')
        before_tenants=len(superuser.state['tenants'])
        superuser.command('createSampleWorkspace')
        examples=[s for s in superuser.state['sites'] if s.get('sample') and not s.get('presentation')]
        assert len(examples)==3
        assert len(superuser.state['tenants'])==before_tenants+1
        assert all(len(s['published'])>60 and len(s['versions'])==1 for s in examples)
        assert all(sum(t.get('sample',False) for t in s['tickets'])==30 for s in examples)
        assert all({c['floor'] for c in s['draft']}=={1,2} for s in examples)
        superuser.command('createSampleWorkspace',expected=400)
        assert len(superuser.state['tenants'])==before_tenants+1
        other.request('state')
        assert not any(s.get('sample') for s in other.state['sites'])
        # All fee/payment operations are authorized against the selected customer site.
        sample_id=examples[0]['id']
        owner.command('sampleTime',expected=403,siteId=sid,minutes=60)
        owner.request('quote',{'siteId':sample_id,'query':'unknown'},expected=403)
        superuser.command('checkin',siteId=sample_id,plate='PAID-TEST',slotId='A4',vehicleType='CAR')
        superuser.command('sampleTime',siteId=sample_id,minutes=120)
        quote=superuser.request('quote',{'siteId':sample_id,'query':'PAID-TEST'})
        assert quote['fee']>0
        superuser.command('reportLostTicket',siteId=sample_id,query='PAID-TEST')
        lost=superuser.request('quote',{'siteId':sample_id,'query':'PAID-TEST'})
        assert lost['fee']==quote['fee']+300
        payload=dict(siteId=sample_id,ticketId=lost['ticketId'],expectedFee=lost['fee'],method='CASH',cashTendered=lost['fee']+100,confirmed=True)
        superuser.command('recordPayment',expected=409,**{**payload,'expectedFee':0})
        superuser.command('recordPayment',expected=400,**{**payload,'cashTendered':1})
        superuser.command('recordPayment',expected=403,**{**payload,'confirmed':False})
        superuser.command('recordPayment',**payload)
        assert superuser.state['operationResult']['change']==100
        superuser.command('recordPayment',expected=403,**payload)
        superuser.command('checkout',siteId=sample_id,ticketId=lost['ticketId'])
        paid=next(t for ss in superuser.state['sites'] if ss['id']==sample_id for t in ss['tickets'] if t['ticketId']==lost['ticketId'])
        assert paid['fee']==lost['fee'] and paid['paymentMethod']=='SIMULATED_CASH'
        superuser.command('checkout',expected=400,siteId=sample_id,ticketId=lost['ticketId'])
        superuser.command('sampleTime',siteId=sample_id,minutes=0,reset=True)
        for method in ['PROMPTPAY','CREDIT_CARD']:
            superuser.command('checkin',siteId=sample_id,plate=method,slotId='A4',vehicleType='CAR')
            q=superuser.request('quote',{'siteId':sample_id,'query':method})
            data=dict(siteId=sample_id,ticketId=q['ticketId'],method=method,expectedFee=q['fee'],confirmed=True)
            superuser.command('recordPayment',expected=400,**data,reference='')
            superuser.command('recordPayment',**data,reference='EXTERNAL-RECEIPT-'+method)
            assert superuser.state['operationResult']['paymentMethod']=='SIMULATED_'+method
            superuser.command('checkout',siteId=sample_id,ticketId=q['ticketId'])
        print('Platform HTTP PASS: tenant isolation, roles, layouts, publication guards, transactions, reservations, users, XLSX source data, restart persistence')
    finally:
        process.terminate(); process.wait(timeout=5)
