package com.example.doorlock.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.doorlock.R
import com.example.doorlock.RelayStatusStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studentIdDataStore by preferencesDataStore(name = "doorlock_user_prefs")

/**
 * 학번의 유일한 쓰기 통로.
 *
 * DataStore를 학번의 영속 원본(source of truth)으로 취급하고,
 * 등록/해제는 반드시 이 클래스를 거치도록 강제합니다.
 * (UI 쪽 ViewModel들은 이 Repository의 결과가 성공(Result.success)일 때만
 *  UserSession을 갱신해야 합니다 — DataStore가 먼저, UserSession은 그 다음.)
 *
 * BLE 담당 코드(RelayStatusStore, BleReceiver, BleRelayService, BleScanRegistrar)는
 * 여전히 자체 SharedPreferences(RelayStatusStore)에서 학번을 읽으므로, BLE 쪽 코드를
 * 변경하지 않고도 동일한 학번을 쓸 수 있도록 이 Repository가 두 저장소를 함께 갱신하는
 * "다리" 역할을 합니다. (BLE 프로토콜/로직 자체는 건드리지 않음 — 공개 API만 호출)
 */
class StudentIdRepository(private val context: Context) {

    private val studentIdKey = stringPreferencesKey("student_id")

    /** 저장된 학번을 관찰합니다. 등록된 적이 없으면 null. */
    val studentId: Flow<String?> =
        context.studentIdDataStore.data.map { preferences -> preferences[studentIdKey] }

    /**
     * 학번을 등록합니다. RelayStatusStore가 요구하는 검증 규칙(숫자 10자리)을
     * 이 Repository가 유일한 기준으로 적용합니다.
     */
    suspend fun registerStudentId(id: String): Result<Unit> {
        if (id.length != 10 || !id.all(Char::isDigit)) {
            return Result.failure(IllegalArgumentException(context.getString(R.string.student_id_error)))
        }
        return runCatching {
            context.studentIdDataStore.edit { preferences ->
                preferences[studentIdKey] = id
            }
            RelayStatusStore.setStudentId(context, id)
        }
    }

    /** 등록된 학번을 삭제합니다 (등록 해제). */
    suspend fun clearStudentId(): Result<Unit> = runCatching {
        context.studentIdDataStore.edit { preferences ->
            preferences.remove(studentIdKey)
        }
        // 다음 등록 시 CDM 페어링/스캔 등록을 처음부터 다시 진행하도록 초기화.
        RelayStatusStore.setInitialSetupComplete(context, false)
    }
}
