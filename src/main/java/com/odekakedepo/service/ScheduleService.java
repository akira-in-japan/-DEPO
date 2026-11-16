package com.odekakedepo.service;

// 予定表の処理
// 【service】画面の処理の中身（ルール・計算）を担当するクラス。DB は repository を通して使う
// @Service を付けると、Spring がインスタンスを1つ作って管理し、Controller などに渡してくれる（DI）

import com.odekakedepo.entity.Schedule;
import com.odekakedepo.form.ScheduleForm;
import com.odekakedepo.repository.ScheduleRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ScheduleService {

    // 使う repository
    private final ScheduleRepository scheduleRepository;

    public ScheduleService(ScheduleRepository scheduleRepository) {
        this.scheduleRepository = scheduleRepository;
    }

    // 計画の予定をすべて（並べ方は SQL で決めている）
    public List<Schedule> findByOutingId(long outingId) {
        return scheduleRepository.findByOutingId(outingId);
    }

    // 予定を追加する
    public void add(long outingId, ScheduleForm form) {
        scheduleRepository.insert(outingId, form.getStartTime(), form.getContent(), form.getPlace());
    }

    // 予定を削除する
    public void delete(long outingId, long scheduleId) {
        scheduleRepository.delete(outingId, scheduleId);
    }
}
