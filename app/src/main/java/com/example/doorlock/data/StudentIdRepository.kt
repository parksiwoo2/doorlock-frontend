package com.example.doorlock.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 기기에 등록된 학번을 영속 저장하는 DataStore.
 *
 * 이 앱은 더 이상 "로그인 세션"이 아니라 "기기에 등록된 사용자 식별 정보"라는
 * 개념으로 학번을 다루므로, 앱 프로세스가 종료되었다가 다시 실행되어도
 * 값이 유지되어야 합니다. SharedPreferences 대신 Jetpack DataStore(Preferences)를 사용합니다.
 *
 * 주의: BLE 쪽에서 사용 중인 [com.example.doorlock.RelayStatusStore]의 학번 저장 로직과는
 * 별개입니다. BLE 프로토콜/저장 방식은 BLE 담당 팀원의 영역이라 이번 작업에서 건드리지 않았습니다.
 * BLE 쪽에 학번을 전달해야 하는 시점이 오면, 이 Repository에서 값을 읽어 전달하는
 * 연결 지점만 추가하면 됩니다 (아래 TODO 참고).
 */
private val Context.studentIdDataStore by preferencesDataStore(name = "doorlock_user_prefs")

class StudentIdRepository(private val context: Context) {

    private val studentIdKey = stringPreferencesKey("student_id")

    /** 저장된 학번을 관찰합니다. 등록된 적이 없으면 null. */
    val studentId: Flow<String?> =
        context.studentIdDataStore.data.map { preferences -> preferences[studentIdKey] }

    /** 학번을 기기에 등록(영속 저장)합니다. */
    suspend fun registerStudentId(studentId: String): Result<Unit> {
        return try {
            context.studentIdDataStore.edit { preferences ->
                preferences[studentIdKey] = studentId
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 등록된 학번을 삭제합니다 (등록 해제). */
    suspend fun clearStudentId(): Result<Unit> {
        return try {
            context.studentIdDataStore.edit { preferences ->
                preferences.remove(studentIdKey)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // TODO(BLE 연동): 라즈베리파이로 학번을 암호화하여 전달하는 시점이 확정되면,
    // 이 studentId 값을 BLE 담당 팀원의 모듈(예: BleRelayService)에 전달하는
    // 인터페이스를 여기에 추가합니다. 암호화 방식/패킷 구조는 이번 작업 범위 밖입니다.
}
