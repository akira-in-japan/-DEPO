package com.odekakedepo.controller;

// S05 おでかけしおり・思い出（写真・動画）・共有
// 【controller】ブラウザからのリクエスト（URL）を受け取り、service に処理を頼んで、表示する画面（テンプレート）を決めるクラス
// @Controller：戻り値の文字列が「表示するテンプレートの名前」になる（例："outings/list" → templates/outings/list.html）
// "redirect:/…" を返すと、ブラウザにその URL へ移動してもらう（保存のあとに使うと、再読み込みで二重に保存されない）

import com.odekakedepo.entity.Outing;
import com.odekakedepo.entity.PackingItem;
import com.odekakedepo.entity.Photo;
import com.odekakedepo.service.OutingService;
import com.odekakedepo.service.PackingItemService;
import com.odekakedepo.service.PhotoService;
import com.odekakedepo.service.ScheduleService;
import com.odekakedepo.service.WishService;
import java.util.List;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

// S05 おでかけしおり。表示専用で、データは毎回、計画・予定・持ち物から作る。
// 思い出（日付が過ぎた計画）は、しおり＋写真・動画のアップロード＋共有だけができる。
@Controller
public class ShioriController {

    // 使うサービス
    private final OutingService outingService;
    private final PackingItemService itemService;
    private final WishService wishService;
    private final ScheduleService scheduleService;
    private final PhotoService photoService;

    // コンストラクタ：Spring が必要なサービスを自動で渡してくれる（DI）
    public ShioriController(OutingService outingService, PackingItemService itemService, WishService wishService,
                            ScheduleService scheduleService, PhotoService photoService) {
        this.outingService = outingService;
        this.itemService = itemService;
        this.wishService = wishService;
        this.scheduleService = scheduleService;
        this.photoService = photoService;
    }

    // U13 しおり（これからの計画）。思い出なら思い出の画面へ
    @GetMapping("/outings/{outingId}/shiori")
    public String shiori(@PathVariable long outingId, Model model) {
        Outing outing = find(outingId);
        if (OutingController.isMemory(outing)) {
            return "redirect:/outings/" + outingId + "/memory";
        }
        return show(outing, "plan", model);
    }

    // 思い出：しおり＋写真・動画＋共有。まだ日付が来ていない計画なら計画詳細へ
    // uploadError=size は、ファイルが大きすぎたとき UploadErrorHandler から戻ってきた印
    @GetMapping("/outings/{outingId}/memory")
    public String memory(@PathVariable long outingId, @RequestParam(required = false) String uploadError, Model model) {
        Outing outing = find(outingId);
        if (!OutingController.isMemory(outing)) {
            return "redirect:/outings/" + outingId; // まだ日付が来ていない計画は、ふつうの計画詳細へ
        }
        if ("size".equals(uploadError)) {
            model.addAttribute("uploadErrors", List.of("ファイルが大きすぎます。1つ50MBまで、1回で合計200MBまでです。"));
        }
        return show(outing, "memory", model);
    }

    // 共有されたしおり（見るだけ）。推測できない token の URL でしか開けない
    @GetMapping("/share/{token}")
    public String shared(@PathVariable String token, Model model) {
        Outing outing = outingService.findByShareToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return show(outing, "shared", model);
    }

    // 写真・動画をアップロード（思い出のみ）。List<MultipartFile> で複数のファイルを受け取る
    // 結果のメッセージはフラッシュ属性で思い出の画面に渡す
    @PostMapping("/outings/{outingId}/photos")
    public String upload(@PathVariable long outingId,
                         @RequestParam(name = "files", required = false) List<MultipartFile> files,
                         RedirectAttributes ra) {
        Outing outing = find(outingId);
        if (!OutingController.isMemory(outing)) {
            return "redirect:/outings/" + outingId;
        }
        if (files == null || files.stream().allMatch(MultipartFile::isEmpty)) {
            ra.addFlashAttribute("uploadErrors", List.of("写真か動画を選んでください"));
        } else {
            List<String> errors = photoService.upload(outingId, files);
            if (!errors.isEmpty()) {
                ra.addFlashAttribute("uploadErrors", errors);
            } else {
                ra.addFlashAttribute("uploadDone", true);
            }
        }
        return "redirect:/outings/" + outingId + "/memory#photos";
    }

    // 写真・動画を1つ消す
    @PostMapping("/outings/{outingId}/photos/{photoId}/delete")
    public String deletePhoto(@PathVariable long outingId, @PathVariable long photoId) {
        photoService.delete(outingId, photoId);
        return "redirect:/outings/" + outingId + "/memory#photos";
    }

    // 写真・動画のファイルそのものを返す（<img> や <video> の src に使う）
    // ResponseEntity で、中身と一緒に Content-Type やキャッシュの指示も返す。期限切れは 404
    @GetMapping("/photos/{token}")
    public ResponseEntity<FileSystemResource> photo(@PathVariable String token) {
        Photo photo = photoService.findActiveByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        FileSystemResource file = new FileSystemResource(photoService.filePath(photo));
        if (!file.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .cacheControl(CacheControl.noStore())
                .body(file);
    }

    // 3つの画面で共通の準備。mode：plan（これからの計画）／memory（思い出）／shared（共有された人）
    // readOnly：編集ボタンを出さないか。photos：plan のときは写真を出さない
    private String show(Outing outing, String mode, Model model) {
        List<PackingItem> items = itemService.findByOutingId(outing.id());
        long prepared = items.stream().filter(PackingItem::prepared).count();
        model.addAttribute("outing", outing);
        model.addAttribute("mode", mode);
        model.addAttribute("readOnly", !"plan".equals(mode));
        model.addAttribute("schedules", scheduleService.findByOutingId(outing.id()));
        model.addAttribute("items", items);
        model.addAttribute("progress", items.isEmpty() ? 0 : Math.round(prepared * 100f / items.size()));
        model.addAttribute("wishes", wishService.findByOutingId(outing.id()));
        model.addAttribute("photos", "plan".equals(mode) ? List.of() : photoService.findActive(outing.id()));
        model.addAttribute("keepDays", photoService.getKeepDays());
        model.addAttribute("maxPhotos", PhotoService.MAX_PER_OUTING);
        // 共有用の URL を「今のサーバーのアドレス＋/share/token」で作る
        model.addAttribute("shareUrl", ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/share/{token}").buildAndExpand(outing.shareToken()).toUriString());
        return "outings/shiori";
    }

    // 計画を探し、なければ 404 にする
    private Outing find(long id) {
        return outingService.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
