/* ==========================================================
   計画詳細（S03）detail.js
   画面を再読み込みせずに保存する（fetch で API を呼ぶ）もの：
   ・持ち物の追加・削除・個数・準備チェック（「済」のスタンプ）
   ・やりたいことの追加・削除
   ・予定の追加・削除
   用意のしかた（DEPOで買う／別で準備）は、商品を選ぶ画面で決める
   ========================================================== */
(function () {
  // 'use strict'：まちがえやすい書き方をエラーにして、バグを早く見つける
  'use strict';
  // 計画詳細の画面でなければ（#detail がなければ）何もしない。計画 ID は HTML の data-outing-id から読む
  const root = document.getElementById('detail');
  if (!root) return;
  const outingId = root.dataset.outingId;

  // API を呼ぶ共通の関数。async/await で「返事が来るまで待つ」を読みやすく書ける
  async function callApi(url, method, body) {
    const res = await fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: body ? JSON.stringify(body) : undefined
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.message || '保存できませんでした。もう一度お試しください。'); // fetch は 400/500 でも失敗にならないので自分で判定
    return data;
  }

  // でぽんたに話させる（odekake-ui.js が box.tanukiSay を用意している）
  const tanukiSay = text => {
    const box = document.querySelector('.guide-col [data-tanuki]');
    if (box && box.tanukiSay) box.tanukiSay(text);
  };

  const list = document.getElementById('checklist');
  const itemInput = document.getElementById('new-item');
  const itemError = document.getElementById('item-error');

  /* ---------- 持ち物の件数と、準備の進み具合を数え直す ---------- */
  let lastPct = null;
  function refresh() {
    const rowsItem = [...list.querySelectorAll(':scope > li')];
    const done = rowsItem.filter(li => li.querySelector('input[type="checkbox"]').checked).length;
    const pct = rowsItem.length ? Math.round(done / rowsItem.length * 100) : 0;
    document.getElementById('item-summary').textContent = `${rowsItem.length}件（準備済み${done}件／未準備${rowsItem.length - done}件）`;
    document.getElementById('item-empty').hidden = rowsItem.length > 0;

    // 準備の進み具合が変わったら、でぽんたが教えてくれる
    if (lastPct !== null && pct !== lastPct && rowsItem.length) {
      tanukiSay(pct === 100 ? '準備100％！ばっちりだね！' : `準備は${pct}％になったよ！`);
    }
    lastPct = pct;
  }

  // 持ち物リストの1行（チェック・名前・個数・用意のしかた・削除）を作る
  function createItemRow(item) {
    const li = document.createElement('li');
    li.dataset.itemId = item.id;
    li.innerHTML = `<input type="checkbox" class="stamp"><label class="name"></label>
      <span class="prep-tag none">まだ決まっていない</span>
      <span class="qty" aria-label="個数"><button type="button" class="qty-minus">−</button><span class="qty-num"></span><button type="button" class="qty-plus">＋</button></span>
      <button class="del delete-item" type="button">削除</button>`;
    const box = li.querySelector('input');
    box.id = `item-${item.id}`; box.checked = item.prepared;
    box.setAttribute('aria-label', `${item.name}の準備が済んだ`);
    const label = li.querySelector('label');
    label.htmlFor = box.id; label.textContent = item.name; // textContent なので入力した文字がそのまま表示される
    li.querySelector('.qty-num').textContent = item.quantity;
    li.querySelector('.qty-minus').setAttribute('aria-label', `${item.name}を1つへらす`);
    li.querySelector('.qty-plus').setAttribute('aria-label', `${item.name}を1つふやす`);
    li.querySelector('.delete-item').setAttribute('aria-label', `${item.name}を削除`);
    li.dataset.prep = item.prepMethod || '';
    li.dataset.hasProduct = item.productId != null;
    markUndecided(li);
    return li;
  }

  // 決まっていない持ち物に色を付ける：済でなく、用意のしかたが決まっていない（または DEPO で買うのに商品がまだ）とき
  // 済を外したときも、まだ決まっていなければ色が戻る
  function markUndecided(li) {
    const done = li.querySelector('input[type="checkbox"]').checked;
    const prep = li.dataset.prep;
    const undecided = !done && (prep === '' || (prep === 'store' && li.dataset.hasProduct !== 'true'));
    li.classList.toggle('undecided', undecided);
    // 札の文字：準備完了！／DEPOで商品を選択した／別で準備する／まだ決まっていない（どれも同じ長さで表示）
    const tag = li.querySelector('.prep-tag');
    if (tag) {
      const state = done ? 'done' : prep === 'other' ? 'other'
          : (prep === 'store' && li.dataset.hasProduct === 'true') ? 'store' : 'none';
      tag.className = `prep-tag ${state}`;
      tag.textContent = { done: '準備完了！', other: '別で準備する', store: 'DEPOで商品を選択した', none: 'まだ決まっていない' }[state];
    }
  }

  async function addItem(name, quantity = 1) {
    const item = await callApi(`/api/outings/${outingId}/items`, 'POST', { name, quantity });
    list.appendChild(createItemRow(item));
    refresh();
    return item;
  }

  // 最初に1回、件数と準備の進み具合を数えておく
  refresh();

  /* ---------- 持ち物の追加 ---------- */
  document.getElementById('add-item').addEventListener('click', async () => {
    const name = itemInput.value.trim();
    if (!name) { itemError.textContent = '持ち物の名前を入力してください'; return; }
    const qtyInput = document.getElementById('new-qty');
    const quantity = parseInt(qtyInput.value, 10) || 1;
    try {
      await addItem(name, quantity);
      itemInput.value = '';
      qtyInput.value = 1;
      itemError.textContent = '';
    } catch (e) {
      itemError.textContent = e.message;
    }
  });
  itemInput.addEventListener('keydown', e => {
    if (e.key === 'Enter') { e.preventDefault(); document.getElementById('add-item').click(); }
  });

  /* ---------- 個数の −／＋・用意のしかた・削除 ---------- */
  list.addEventListener('click', async e => {
    const li = e.target.closest('li');
    if (!li) return;
    // 個数（押した瞬間に保存する）
    if (e.target.classList.contains('qty-minus') || e.target.classList.contains('qty-plus')) {
      const numEl = li.querySelector('.qty-num');
      const next = parseInt(numEl.textContent, 10) + (e.target.classList.contains('qty-plus') ? 1 : -1);
      if (next < 1 || next > 99) return;
      try {
        const res = await callApi(`/api/items/${li.dataset.itemId}/quantity`, 'PATCH', { quantity: next });
        numEl.textContent = res.quantity;
        // 個数が変わったら、もう一度準備が必要なので「済」を外す（サーバーでも外している）
        const stamp = li.querySelector('input[type="checkbox"]');
        if (stamp.checked) {
          stamp.checked = false;
          li.classList.remove('done');
          tanukiSay('個数が変わったから、「済」を外したよ。もう一度準備してね');
        }
        markUndecided(li);
        refresh();
        itemError.textContent = '';
      } catch (err) {
        itemError.textContent = err.message;
      }
      return;
    }
    // 削除
    if (!e.target.classList.contains('delete-item')) return;
    if (!confirm('この持ち物を削除しますか？')) return;
    try {
      await callApi(`/api/items/${li.dataset.itemId}`, 'DELETE');
      li.remove();
      refresh();
      itemError.textContent = '';
    } catch (err) {
      itemError.textContent = err.message;
    }
  });

  /* ---------- 準備チェック（「済」のスタンプ） ---------- */
  list.addEventListener('change', async e => {
    if (e.target.type !== 'checkbox') return;
    const box = e.target;
    const li = box.closest('li');
    try {
      await callApi(`/api/items/${li.dataset.itemId}/prepared`, 'PATCH', { prepared: box.checked });
      li.classList.toggle('done', box.checked);
      markUndecided(li); // 済なら色を外し、済を外して決まっていなければ色を戻す
      refresh();
      itemError.textContent = '';
    } catch (err) {
      box.checked = !box.checked; // 失敗したら元に戻す
      itemError.textContent = err.message;
    }
  });

  /* ---------- おすすめから持ち物に追加 ---------- */
  document.querySelectorAll('[data-add-item]').forEach(btn => {
    btn.addEventListener('click', async () => {
      try {
        await addItem(btn.dataset.addItem);
        btn.closest('.rec-item').remove();
        itemError.textContent = '';
      } catch (err) {
        itemError.textContent = err.message;
      }
    });
  });

  /* ---------- やりたいこと ---------- */
  const wishList = document.getElementById('wish-list');
  const wishInput = document.getElementById('new-wish');
  const wishError = document.getElementById('wish-error');

  // 追加したやりたいことの行を作る
  function createWishRow(wish) {
    const li = document.createElement('li');
    li.dataset.wishId = wish.id;
    const text = document.createElement('span');
    text.className = 'text'; text.textContent = wish.content;
    const del = document.createElement('button');
    del.type = 'button'; del.className = 'link-btn delete-wish'; del.textContent = '削除';
    li.append(text, del);
    return li;
  }

  document.getElementById('add-wish').addEventListener('click', async () => {
    const name = wishInput.value.trim();
    if (!name) { wishError.textContent = 'やりたいことを入力してください'; return; }
    try {
      const wish = await callApi(`/api/outings/${outingId}/wishes`, 'POST', { name });
      wishList.appendChild(createWishRow(wish));
      document.getElementById('wish-empty').hidden = true;
      wishInput.value = '';
      wishError.textContent = '';
    } catch (e) {
      wishError.textContent = e.message;
    }
  });
  wishInput.addEventListener('keydown', e => {
    if (e.key === 'Enter') { e.preventDefault(); document.getElementById('add-wish').click(); }
  });
  wishList.addEventListener('click', async e => {
    if (!e.target.classList.contains('delete-wish')) return;
    const li = e.target.closest('li');
    try {
      await callApi(`/api/wishes/${li.dataset.wishId}`, 'DELETE');
      li.remove();
      document.getElementById('wish-empty').hidden = wishList.children.length > 0;
      wishError.textContent = '';
    } catch (err) {
      wishError.textContent = err.message;
    }
  });

  /* ---------- 予定表（再読み込みなし） ---------- */
  const scheduleList = document.getElementById('schedule-list');
  const scheduleError = document.getElementById('schedule-error');

  // サーバーから返ってきた予定の一覧（並べ替え済み）で、表を作り直す
  function renderSchedules(schedules) {
    scheduleList.innerHTML = '';
    schedules.forEach(sc => {
      const li = document.createElement('li');
      li.dataset.scheduleId = sc.id;
      if (!sc.startTime) li.classList.add('undecided');
      li.innerHTML = '<time></time><span></span><span class="place"></span><button class="link-btn delete-schedule" type="button">削除</button>';
      li.querySelector('time').textContent = sc.startTime ? sc.startTime.slice(0, 5) : '時間未定'; // "10:00:00" → "10:00"
      li.querySelector('span').textContent = sc.content;
      li.querySelector('.place').textContent = sc.place || '';
      scheduleList.appendChild(li);
    });
    document.getElementById('schedule-empty').hidden = schedules.length > 0;
  }

  document.getElementById('add-schedule').addEventListener('click', async () => {
    const timeEl = document.getElementById('new-time');
    const contentEl = document.getElementById('new-content');
    const placeEl = document.getElementById('new-place');
    if (!contentEl.value.trim()) { scheduleError.textContent = 'やることを入力してください'; return; }
    try {
      const schedules = await callApi(`/api/outings/${outingId}/schedules`, 'POST',
          { startTime: timeEl.value, content: contentEl.value, place: placeEl.value });
      renderSchedules(schedules);
      tanukiSay(`予定表に「${contentEl.value.trim()}」を入れたよ`);
      timeEl.value = ''; contentEl.value = ''; placeEl.value = '';
      scheduleError.textContent = '';
    } catch (e) {
      scheduleError.textContent = e.message;
    }
  });
  document.getElementById('new-content').addEventListener('keydown', e => {
    if (e.key === 'Enter') { e.preventDefault(); document.getElementById('add-schedule').click(); }
  });
  scheduleList.addEventListener('click', async e => {
    if (!e.target.classList.contains('delete-schedule')) return;
    const li = e.target.closest('li');
    if (!confirm('この予定を削除しますか？')) return;
    try {
      renderSchedules(await callApi(`/api/outings/${outingId}/schedules/${li.dataset.scheduleId}`, 'DELETE'));
      scheduleError.textContent = '';
    } catch (err) {
      scheduleError.textContent = err.message;
    }
  });
})();
