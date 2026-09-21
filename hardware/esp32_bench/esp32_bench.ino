// LED BENCH ONLY. Never connect this output to a barrier, motor, or relay.
// ESP32 Arduino core + ArduinoJson 7. Copy secrets.example.h to secrets.h locally.
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <Preferences.h>
#include <time.h>
#include "secrets.h"
Preferences memory;
unsigned long lastPoll=0;
bool postEvent(const char* kind, const String& commandId, JsonDocument& response) {
  WiFiClientSecure tls;
  tls.setCACert(ROOT_CA); // No setInsecure fallback.
  HTTPClient http;
  http.setTimeout(5000);
  if (!String(ENDPOINT).startsWith("https://") || !http.begin(tls, ENDPOINT)) return false;
  http.addHeader("Content-Type", "application/json");
  http.addHeader("Authorization", String("Bearer ")+DEVICE_TOKEN);
  JsonDocument body;
  body["siteId"]=SITE_ID; body["deviceId"]=DEVICE_ID; body["type"]=kind;
  if(commandId.length()) body["commandId"]=commandId;
  String payload; serializeJson(body,payload);
  int status=http.POST(payload);
  bool ok=false;
  if(status==200) ok=!deserializeJson(response,http.getString());
  http.end(); return ok;
}
void setup() {
  pinMode(BENCH_LED_PIN,OUTPUT); digitalWrite(BENCH_LED_PIN,LOW);
  memory.begin("parking-bench",false);
  WiFi.begin(WIFI_SSID,WIFI_PASSWORD);
  configTime(0,0,"pool.ntp.org","time.nist.gov");
}
void loop() {
  if(WiFi.status()!=WL_CONNECTED || time(nullptr)<1700000000) {delay(1000);return;}
  if(millis()-lastPoll<30000) {delay(10);return;}
  lastPoll=millis();
  JsonDocument response;
  if(!postEvent("HEARTBEAT","",response)) return;
  if(String(response["command"]["type"] | "")!="LED_PULSE") return;
  String id=response["command"]["id"] | "";
  if(id.length()!=36) return;
  if(id!=memory.getString("lastCommand","")) {
    // Persist BEFORE actuation: reboot/network retries cannot repeat the pulse.
    if(memory.putString("lastCommand",id)==0) return;
    digitalWrite(BENCH_LED_PIN,HIGH);delay(500);digitalWrite(BENCH_LED_PIN,LOW);
  }
  JsonDocument ack;
  postEvent("ACK",id,ack);
}
