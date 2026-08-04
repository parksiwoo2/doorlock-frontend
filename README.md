# doorlock-frontend
app/src/main/java/com/example/doorlock/BleConstans.kt
- TARGET_UUID가 앱에서 수신하는 uuid(라즈베리파이에서 송출할 ble uuid)
- RESPONSE_UUID가 앱이 TERGET_UUID를 송신받는 즉시 지속적으로 송출할 ble uuid

### 최초 앱설치 후
- 학번 입력
- 권한 허용
- companion device manager설정 (기기에서 ble송출을 하고 앱에서 기기를 등록)
### 이후엔 백그라운드에서도 자동 감지가 됨