/* ==========================================================
   おでかけDEPO 画面の動き（見た目だけ）odekake-ui.js
   ・DB への保存はしない（保存は detail.js が API を呼んで行う）
   ・ページにその部品がなければ、何もしない
   ========================================================== */
// (function () { … })(); は「すぐ実行する関数」。中の変数がほかのファイルとぶつからないように包んでいる
(function () {
  'use strict';

  /* ---------- 計画一覧：種類のタブで絞り込む ---------- */
  // data-filter が付いたタブを全部取り、押されたら同じ種類（data-kind）のカードだけを表示する
  const tabs = document.querySelectorAll('.tabs [data-filter]');
  tabs.forEach(tab => {
    tab.addEventListener('click', e => {
      e.preventDefault();
      const kind = tab.dataset.filter;
      tabs.forEach(t => t.removeAttribute('aria-current'));
      tab.setAttribute('aria-current', 'page');
      let shown = 0;
      document.querySelectorAll('#plan-list .plan-card').forEach(card => {
        const hit = kind === 'all' || card.dataset.kind === kind;
        card.hidden = !hit;
        if (hit) shown++;
      });
      const count = document.getElementById('plan-count');
      if (count) count.textContent = shown;
    });
  });

  /* ---------- 人数の −／＋ ---------- */
  document.querySelectorAll('.stepper').forEach(stepper => {
    const input = stepper.querySelector('input');
    const [minus, plus] = stepper.querySelectorAll('button');
    const change = diff => {
      input.value = Math.max(1, (parseInt(input.value, 10) || 1) + diff);
      input.dispatchEvent(new Event('input', { bubbles: true }));
    };
    minus.addEventListener('click', () => change(-1));
    plus.addEventListener('click', () => change(1));
  });

  /* ---------- ダイアログを開く・閉じる（data-open="id" / data-close） ---------- */
  document.addEventListener('click', e => {
    const opener = e.target.closest('[data-open]');
    if (opener) {
      const d = document.getElementById(opener.dataset.open);
      if (d) { d.showModal(); d.dispatchEvent(new Event('opened')); }
    }
    const closer = e.target.closest('[data-close]');
    if (closer) closer.closest('dialog').close();
  });

  /* ---------- 削除の確認（data-confirm="メッセージ"） ---------- */
  document.addEventListener('submit', e => {
    const msg = e.target.dataset.confirm;
    if (msg && !confirm(msg)) e.preventDefault();
  });

  /* ---------- 計画をつくる画面 ---------- */
  const form = document.getElementById('outing-form');
  if (form) {
    const KINDS = {
      'キャンプ':   { cls: 'camp',   icon: 'i-tent',     from: '#f6efd9' },
      '旅行':       { cls: 'travel', icon: 'i-suitcase', from: '#eef5fb' },
      'ピクニック': { cls: 'picnic', icon: 'i-basket',   from: '#fff6ea' }
    };
    const $ = id => document.getElementById(id);
    const week = ['日', '月', '火', '水', '木', '金', '土'];
    // 今選ばれている種類（ラジオボタン）を返す
    const selectedKind = () => {
      const c = form.querySelector('input[name="category"]:checked');
      return c ? c.value : 'キャンプ';
    };

    // 右の「計画のイメージ」
    // 入力に合わせて、右の「計画のイメージ」の文字と色を書きかえる
    const updatePreview = () => {
      if (!$('pv-title')) return;
      $('pv-title').textContent = $('title').value.trim() || 'タイトル';
      const d = $('outingDate').value;
      if (d) {
        const [y, m, day] = d.split('-').map(Number);
        $('pv-date').textContent = `${y}年${m}月${day}日（${week[new Date(y, m - 1, day).getDay()]}）`; // いつも西暦
      } else {
        $('pv-date').textContent = '日付を選んでください';
      }
      $('pv-place').textContent = $('destination').value.trim() || '行き先';
      $('pv-people').textContent = $('participants').value || '1';
      const name = selectedKind();
      const k = KINDS[name];
      $('pv-kind').textContent = name;
      $('pv-kind').className = 'kind ' + k.cls;
      $('pv-kind').style.background = `var(--${k.cls})`;
      $('pv-visual').style.background = `linear-gradient(160deg, ${k.from}, var(--${k.cls}))`;
      $('pv-visual').style.color = `var(--${k.cls}-ink)`;
      $('pv-icon').className = `ico ${k.icon} big`;
    };

    // 持ち物テンプレート（新規作成のときだけ）。中身はサーバーから window.ODEKAKE_TEMPLATES で受け取る
    const tplList = $('tpl-new');
    let tplKind = null;
    // 選んだ種類のテンプレートを、見出しつきのチェックボックスで表示する（種類が変わったときだけ作り直す）
    const renderTemplate = () => {
      if (!tplList || !window.ODEKAKE_TEMPLATES) return;
      const kind = selectedKind();
      if (kind === tplKind) return;
      tplKind = kind;
      tplList.innerHTML = '';
      let group = null;
      (window.ODEKAKE_TEMPLATES[kind] || []).forEach((t, i) => {
        if (t.group !== group) { // グループの見出し（基本装備・調理・食事 など）
          group = t.group;
          const head = document.createElement('li');
          head.className = 'tpl-group';
          head.textContent = group;
          tplList.append(head);
        }
        const li = document.createElement('li');
        const box = document.createElement('input');
        box.type = 'checkbox'; box.name = 'templateItems'; box.value = t.name; box.id = `tpl-${i}`; box.checked = false; // 最初はオフ。お客さんが選んだものだけ入る
        box.setAttribute('form', 'outing-form'); // フォームの外にあっても一緒に送る
        const label = document.createElement('label');
        label.htmlFor = box.id; label.textContent = t.name;
        li.append(box, label);
        tplList.append(li);
      });
      const title = $('tpl-kind');
      if (title) title.textContent = kind;
    };

    // やりたいこと：入力欄を増やす
    const wishBox = $('wish-inputs');
    const addWish = $('add-wish');
    if (wishBox && addWish) {
      addWish.addEventListener('click', () => {
        const row = document.createElement('div');
        row.className = 'row';
        const input = document.createElement('input');
        input.type = 'text'; input.name = 'wishes'; input.maxLength = 100;
        input.placeholder = '例：温泉に入る、写真を撮る';
        input.setAttribute('aria-label', 'やりたいこと');
        row.append(input);
        wishBox.append(row);
        input.focus();
      });
    }

    // 日付のカレンダー：flatpickr が読み込めたら使う（読み込めないときはふつうの文字入力のまま）
    const dateInput = $('outingDate');
    if (window.flatpickr && dateInput) {
      flatpickr(dateInput, {
        locale: 'ja',                     // 曜日や月を日本語で
        dateFormat: 'Y-m-d',              // サーバーに送る形（2026-11-12）
        altInput: true,                   // 画面には別の見やすい形で表示する
        altFormat: 'Y年n月j日（D）',       // 画面の表示（2026年11月12日（木））。和暦は使わない
        minDate: dateInput.dataset.min || null, // 新規作成のときは今日より前を選べない
        disableMobile: true,              // スマホでも同じカレンダーを使う（和暦の表示を避ける）
        onChange: () => updatePreview()   // 日付を選んだら右のイメージも変える
      });
      // 画面に出ているのは flatpickr が作った別の入力欄なので、そちらにも狸の説明を付ける
      if (dateInput._flatpickr && dateInput._flatpickr.altInput) {
        dateInput._flatpickr.altInput.dataset.guide = dateInput.dataset.guide;
      }
    }

    form.addEventListener('input', updatePreview);
    form.addEventListener('change', () => { updatePreview(); renderTemplate(); });
    updatePreview();
    renderTemplate();
  }

  /* ---------- しおりの共有（URL・QR） ---------- */
  const share = document.getElementById('share-dialog');
  if (share) {
    const input = document.getElementById('share-link');
    const qr = document.getElementById('qr');
    const copied = document.getElementById('copied');
    share.addEventListener('opened', () => {
      qr.innerHTML = '';
      copied.textContent = '';
      if (window.QRCode) {
        new QRCode(qr, { text: input.value, width: 180, height: 180, colorDark: '#155e57', colorLight: '#ffffff' });
      } else {
        qr.textContent = 'QRコードを表示できませんでした（ネットにつながっているか確認してください）';
      }
    });
    document.getElementById('copy-link').addEventListener('click', async () => {
      try {
        await navigator.clipboard.writeText(input.value);
        copied.textContent = 'URLをコピーしました';
      } catch (e) {
        input.select();
        copied.textContent = 'URLを選択しました。Ctrl＋C でコピーしてください';
      }
    });
  }

  /* ---------- 狸のキャラクター：6枚の絵を切りかえて動かし、セリフを話す ---------- */
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const FRAMES = ['normal', 'talk', 'happy', 'jump-start', 'jump-air', 'jump-air2'];
  document.querySelectorAll('[data-tanuki]').forEach(box => {
    const textEl = box.querySelector('.tanuki-text');
    const btn = box.querySelector('.tanuki-btn');
    const img = box.querySelector('.tanuki-img');
    const base = box.dataset.base; // 絵のフォルダ（/images/tanuki/）
    const lines = [...box.querySelectorAll('.tanuki-lines li')].map(li => li.textContent);
    let index = 0, typingTimer = null, cycleTimer = null, talkTimer = null;
    let jumping = false, hovering = false;

    // 6枚の絵を先に読みこんでおく（切りかえたときにちらつかないように）
    FRAMES.forEach(f => { const pre = new Image(); pre.src = `${base}${f}.webp`; });
    const setFrame = f => { img.src = `${base}${f}.webp`; };
    // 今の状態に合った絵に戻す（ホバー中は笑顔、それ以外は通常）
    const restFrame = () => { if (!jumping) setFrame(hovering ? 'happy' : 'normal'); };

    // 話しているあいだ、口を開けた絵（talk）と通常の絵を交互に出す
    const startTalking = () => {
      if (reduceMotion) return;
      clearInterval(talkTimer);
      btn.classList.add('talking');
      let open = false;
      talkTimer = setInterval(() => { if (!jumping) { open = !open; setFrame(open ? 'talk' : 'normal'); } }, 160);
    };
    const stopTalking = () => { clearInterval(talkTimer); btn.classList.remove('talking'); restFrame(); };

    // セリフを1文字ずつ表示する。動きを減らす設定のときは、一度に全部出す
    const say = text => {
      clearInterval(typingTimer);
      if (reduceMotion) { textEl.textContent = text; return; }
      textEl.textContent = '';
      textEl.classList.add('typing');
      startTalking();
      let i = 0;
      typingTimer = setInterval(() => {
        textEl.textContent = text.slice(0, ++i);
        if (i >= text.length) { clearInterval(typingTimer); textEl.classList.remove('typing'); stopTalking(); }
      }, 45);
    };

    // ジャンプ：踏み切り → 空中 → 空中2 → 笑顔で着地 → 通常（CSS の .jump で上下の動きとつぶれを付ける）
    const jump = () => {
      if (reduceMotion || jumping) return;
      jumping = true;
      clearInterval(talkTimer);
      btn.classList.remove('jump'); void btn.offsetWidth; btn.classList.add('jump');
      const steps = [['jump-start', 0], ['jump-air', 110], ['jump-air2', 330], ['happy', 560]];
      steps.forEach(([f, t]) => setTimeout(() => setFrame(f), t));
      setTimeout(() => { jumping = false; btn.classList.remove('jump'); restFrame(); }, 900);
    };

    // 次のセリフへ（7秒ごと。タップしたときもすぐ次へ）
    const next = () => { index = (index + 1) % lines.length; say(lines[index]); };
    const startCycle = () => { clearInterval(cycleTimer); if (lines.length > 1) cycleTimer = setInterval(next, 7000); };

    // ホバー（マウス）で笑顔、離れたら通常に戻す
    btn.addEventListener('pointerenter', e => { if (e.pointerType === 'mouse') { hovering = true; restFrame(); } });
    btn.addEventListener('pointerleave', () => { hovering = false; restFrame(); });
    // クリック・タップでジャンプして、次のセリフへ
    btn.addEventListener('click', () => { jump(); next(); startCycle(); });

    say(lines[0] || '');
    startCycle();

    box.tanukiSay = text => { say(text); startCycle(); }; // ほかの処理（detail.js など）から話させるため

    // 計画をつくる画面：入力する欄を選ぶと、その欄の説明（data-guide）を話す
    const form = document.getElementById('outing-form');
    if (form) {
      form.addEventListener('focusin', e => {
        const target = e.target.closest('[data-guide]');
        if (!target) return;
        say(target.dataset.guide);
        startCycle(); // 説明を読んでいる間に、次のセリフに変わらないよう数え直す
      });
    }

    // 計画一覧：カードにマウスを乗せる（スマホはタップする）と、そのカードのこと（data-say）を話す
    let lastSay = null;
    const sayCard = card => {
      if (!card || card === lastSay) return;
      lastSay = card;
      say(card.dataset.say);
      startCycle();
    };
    document.querySelectorAll('[data-say]').forEach(card => {
      card.addEventListener('pointerenter', e => { if (e.pointerType === 'mouse') sayCard(card); });
      card.addEventListener('focusin', () => sayCard(card));
      card.addEventListener('touchstart', () => sayCard(card), { passive: true });
      card.addEventListener('pointerleave', () => { if (lastSay === card) lastSay = null; });
    });
  });

  /* ---------- 送信しても同じ位置に戻る（data-keep-scroll の付いたフォーム、data-keep-scroll-links の中のリンク） ---------- */
  // 送信の直前に、ページと左の一覧のスクロール位置を sessionStorage（このタブだけの一時的な保存場所）に入れておき、
  // 次のページを開いたときに、同じ画面（パス）なら元の位置に戻す
  const KEY = 'odekake-scroll';
  const saveScroll = () => {
    const side = document.querySelector('.sticky-side');
    sessionStorage.setItem(KEY, JSON.stringify({ path: location.pathname, y: window.scrollY, side: side ? side.scrollTop : 0 }));
  };
  document.addEventListener('submit', e => { if (e.target.matches('[data-keep-scroll]')) saveScroll(); });
  document.addEventListener('click', e => { if (e.target.closest('[data-keep-scroll-links] a')) saveScroll(); });
  try {
    const saved = JSON.parse(sessionStorage.getItem(KEY) || 'null');
    sessionStorage.removeItem(KEY);
    if (saved && saved.path === location.pathname) {
      if ('scrollRestoration' in history) history.scrollRestoration = 'manual';
      const side = document.querySelector('.sticky-side');
      window.scrollTo(0, saved.y);
      if (side) side.scrollTop = saved.side;
    }
  } catch (e) { /* 保存できない環境では何もしない */ }

  /* ---------- 在庫確認ボタン：管理者ページで入れた在庫数から「在庫あり／残りわずか／在庫なし」を表示する ---------- */
  document.addEventListener('click', e => {
    const btn = e.target.closest('.stock-btn');
    if (!btn) return;
    const result = btn.parentElement.querySelector('.stock-result');
    const stock = btn.dataset.stock;
    result.textContent = btn.dataset.stockLabel;
    result.className = 'stock-result ' + (stock === '' || stock === undefined ? 'unknown' : Number(stock) === 0 ? 'none' : Number(stock) <= 3 ? 'few' : 'ok');
    result.hidden = false;
    // でぽんたも教えてくれる
    const card = btn.closest('.product');
    const name = card ? card.querySelector('h3').textContent : '';
    const box = document.querySelector('.guide-col [data-tanuki]');
    if (box && box.tanukiSay) box.tanukiSay(`「${name}」は、${btn.dataset.stockLabel}`);
  });

  /* ---------- スマホ：左のメニューを、左から出てくる引き出し（ドロワー）にする ----------
     スマホでは左の列（.side）がかくれるので、メニューのある画面だけ左下に「メニュー」ボタンを出す。
     押すと左から出てくる。外側・メニューの中のリンク・Esc キーで閉じる */
  const drawer = document.querySelector('.app > aside.side:not(.sticky-side):not(.side-welcome)');
  if (drawer && drawer.querySelector('.side-nav a')) {
    drawer.id = drawer.id || 'side-drawer';
    const openBtn = document.createElement('button');
    openBtn.type = 'button';
    openBtn.className = 'drawer-btn';
    openBtn.setAttribute('aria-controls', drawer.id);
    openBtn.setAttribute('aria-expanded', 'false');
    openBtn.innerHTML = '<span class="drawer-bars" aria-hidden="true"></span>メニュー';
    const shade = document.createElement('div');
    shade.className = 'drawer-shade';
    document.body.append(openBtn, shade);
    const setOpen = open => {
      document.body.classList.toggle('drawer-open', open);
      openBtn.setAttribute('aria-expanded', String(open));
      if (open) { const first = drawer.querySelector('a, button'); if (first) first.focus(); } else { openBtn.focus({ preventScroll: true }); }
    };
    openBtn.addEventListener('click', () => setOpen(!document.body.classList.contains('drawer-open')));
    shade.addEventListener('click', () => setOpen(false));
    drawer.addEventListener('click', e => { if (e.target.closest('a')) document.body.classList.remove('drawer-open'); });
    document.addEventListener('keydown', e => { if (e.key === 'Escape' && document.body.classList.contains('drawer-open')) setOpen(false); });
  }
})();
