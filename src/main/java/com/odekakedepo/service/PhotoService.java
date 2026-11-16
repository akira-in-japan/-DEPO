package com.odekakedepo.service;

// 思い出の写真・動画（保存・表示・7日で自動削除）の処理
// 【service】画面の処理の中身（ルール・計算）を担当するクラス。DB は repository を通して使う
// @Service を付けると、Spring がインスタンスを1つ作って管理し、Controller などに渡してくれる（DI）

import com.odekakedepo.entity.Photo;
import com.odekakedepo.repository.PhotoRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

// 思い出の写真・動画。
// ・ファイル本体は app.upload-dir のフォルダに、推測できない名前（token）で保存する
// ・保存してから app.photo-keep-days 日（7日）で自動的に消える（1時間ごとに掃除）
// ・1つの計画に入れられるのは MAX_PER_OUTING 件まで（サーバーの容量を守るため）
@Service
public class PhotoService {

    // Logger：動いている様子をコンソールに書き出す道具（System.out.println より細かく管理できる）
    private static final Logger log = LoggerFactory.getLogger(PhotoService.class);
    // 1つの計画に入れられる写真・動画の上限
    public static final int MAX_PER_OUTING = 30;

    // 受け付けるファイルの拡張子と、その Content-Type（ブラウザに伝えるファイルの種類）
    private static final Map<String, String> ALLOWED = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "gif", "image/gif",
            "webp", "image/webp", "mp4", "video/mp4", "mov", "video/quicktime", "webm", "video/webm");

    // uploadDir：保存するフォルダ、keepDays：何日で消すか
    private final PhotoRepository photoRepository;
    // 写真・動画を保存するフォルダ（application.properties の app.upload-dir）
    private final Path uploadDir;
    // 何日で自動的に消すか（application.properties の app.photo-keep-days。7日）
    private final int keepDays;

    // @Value で application.properties の値を受け取る。: のあとは、見つからないときの値
    // Files.createDirectories で、フォルダがなければ作る
    public PhotoService(PhotoRepository photoRepository,
                        @Value("${app.upload-dir}") String uploadDir,
                        @Value("${app.photo-keep-days:7}") int keepDays) throws IOException {
        this.photoRepository = photoRepository;
        this.uploadDir = Path.of(uploadDir).toAbsolutePath();
        this.keepDays = keepDays;
        Files.createDirectories(this.uploadDir);
    }

    // 何日で消えるか（画面の説明に使う）
    public int getKeepDays() {
        return keepDays;
    }

    // 期限内の写真・動画
    // 計画ごとの写真の件数といちばん早く消える日時（計画一覧の狸のセリフ用）
    public Map<Long, Object[]> summaryByOuting() {
        return photoRepository.summaryByOuting(LocalDateTime.now());
    }

    public List<Photo> findActive(long outingId) {
        return photoRepository.findActiveByOutingId(outingId, LocalDateTime.now());
    }

    // アップロード。うまくいかなかったファイルのメッセージを返す（空ならすべて成功）
    // アップロード。MultipartFile はフォームから送られてきたファイル1つ分
    // うまくいかなかったファイルのメッセージをリストで返す（空ならすべて成功）
    public List<String> upload(long outingId, List<MultipartFile> files) {
        List<String> errors = new ArrayList<>();
        int count = photoRepository.countActive(outingId, LocalDateTime.now());
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            // ファイル名からフォルダ部分を取り除く（パスとしては使わず、表示と拡張子の判定だけに使う）
            String original = file.getOriginalFilename() == null ? "file"
                    : file.getOriginalFilename().replaceAll(".*[/\\\\]", "");
            if (original.length() > 200) {
                original = original.substring(original.length() - 200);
            }
            // 拡張子（最後の「.」より後ろ）を小文字で取り出し、ALLOWED にあるか調べる
            String ext = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1).toLowerCase() : "";
            String type = ALLOWED.get(ext);
            if (type == null) {
                errors.add(original + "：写真（jpg・png・gif・webp）か動画（mp4・mov・webm）を選んでください");
                continue;
            }
            // 上限を超えたら保存しない
            if (count >= MAX_PER_OUTING) {
                errors.add(original + "：1つの計画に入れられるのは" + MAX_PER_OUTING + "件までです");
                continue;
            }
            // ファイル名には元の名前を使わず、推測できない token を使う（同じ名前の上書きや、URL を当てられることを防ぐ）
            String token = UUID.randomUUID().toString().replace("-", "");
            // try-with-resources：使い終わったら自動でファイルを閉じる書き方。Files.copy でフォルダに保存する
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, uploadDir.resolve(token), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                errors.add(original + "：保存できませんでした");
                continue;
            }
            // 今の日時と、keepDays 日後の日時（消える日時）を DB に登録する
            LocalDateTime now = LocalDateTime.now();
            photoRepository.insert(outingId, token, original, type, file.getSize(), now, now.plusDays(keepDays));
            count++;
        }
        return errors;
    }

    // 表示用：期限内のものだけ探す
    public Optional<Photo> findActiveByToken(String token) {
        return photoRepository.findActiveByToken(token, LocalDateTime.now());
    }

    // 保存したファイルの場所（フォルダ＋token）
    public Path filePath(Photo photo) {
        return uploadDir.resolve(photo.token());
    }

    // 写真・動画を1つ消す（ファイルと DB の行の両方）。ほかの計画のものは消さない
    public boolean delete(long outingId, long photoId) {
        Optional<Photo> photo = photoRepository.findById(photoId);
        if (photo.isEmpty() || !photo.get().outingId().equals(outingId)) {
            return false;
        }
        deleteFile(photo.get());
        photoRepository.delete(photoId);
        return true;
    }

    // 計画を消すときに、その計画のファイルをすべて消す（DB の行は CASCADE で消える）
    public void deleteAllFiles(long outingId) {
        photoRepository.findByOutingId(outingId).forEach(this::deleteFile);
    }

    // 期限切れの写真・動画を消す（起動して1分後から、1時間ごと）
    // @Scheduled：決まった間隔で自動実行する。起動して60秒後から、3,600,000ミリ秒（1時間）ごと
    // 期限が切れた写真・動画を、ファイルと DB の行の両方から消す
    @Scheduled(initialDelay = 60_000, fixedDelay = 3_600_000)
    public int deleteExpired() {
        List<Photo> expired = photoRepository.findExpired(LocalDateTime.now());
        for (Photo p : expired) {
            deleteFile(p);
            photoRepository.delete(p.id());
        }
        if (!expired.isEmpty()) {
            log.info("期限切れの写真・動画を {} 件消しました", expired.size());
        }
        return expired.size();
    }

    // ファイルを消す。なければ何もしない（deleteIfExists）。消せなかったらログに残す
    private void deleteFile(Photo p) {
        try {
            Files.deleteIfExists(filePath(p));
        } catch (IOException e) {
            log.warn("ファイルを消せませんでした: {}", p.token(), e);
        }
    }
}
