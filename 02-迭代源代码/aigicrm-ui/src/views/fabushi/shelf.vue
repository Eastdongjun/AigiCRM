<template>
  <div class="fx-page fabushi-shelf">
    <!-- 【2026-11-26 按 PC 原型 v8】我的觉醒 = 原型 P9（觉醒总览）+ P9B（我的书架）。
         原型是两个路由，这里仍是四个页签在一屏里切换（截图工具靠 .tabs .tab 找页签）。
         原型没有页头标题块：原来页头的“去书目浏览”按钮挪到页签这一行的右边，四个页签下都在。 -->
    <div class="tab-row">
      <div class="tabs cat-tabs" role="tablist" aria-label="我的觉醒">
        <button
          v-for="t in tabs"
          :key="t.value"
          type="button"
          role="tab"
          class="tab cat-tab"
          :class="{ active: active === t.value }"
          :aria-selected="active === t.value"
          @click="active = t.value"
        >{{ t.label }}</button>
      </div>
      <el-button class="tab-act" @click="goLibrary">去书目浏览</el-button>
    </div>

    <!-- ============ 觉醒总览（原型 P9） ============ -->
    <section v-if="active === 'awaken'" class="awaken" v-loading="loading">
      <!-- 最上面一排三张板块卡：图标、标题、今天的进度、底部的状态章和按钮 -->
      <div class="aw-cards">
        <article class="aw-card">
          <div class="aw-head"><span class="aw-icon"><ElIcon><Sunrise /></ElIcon></span>每日诵读</div>
          <div class="aw-stat">
            <div>每天朗读背诵一小段经典，可传文字/图片后语音诵读。</div>
            <div>
              <template v-if="awaken.todayRecitations > 0">今日已诵 · 今日 {{ awaken.todayRecitations }} 条</template>
              <template v-else>今日<b>尚未诵读</b></template>
            </div>
            <div>本月诵读 {{ personal.monthSongdu || 0 }} 条</div>
          </div>
          <div class="aw-foot">
            <span :class="awaken.todayRecitations > 0 ? 'done-chip' : 'todo-chip'" title="口径：今天已发布诵读内容就算完成">{{ awaken.todayRecitations > 0 ? '今日完成' : '今日待诵' }}</span>
            <span class="spacer" />
            <el-button :type="awaken.todayRecitations > 0 ? 'default' : 'primary'" size="small" @click="openPublish('songdu')">{{ awaken.todayRecitations > 0 ? '再去诵一段' : '去诵读' }}</el-button>
          </div>
        </article>

        <article class="aw-card">
          <div class="aw-head"><span class="aw-icon"><ElIcon><ChatLineRound /></ElIcon></span>每日随施</div>
          <div class="aw-stat">
            <div>随手上传正能量，免标题、免关联书目。</div>
            <div>
              <template v-if="awaken.todayGifts > 0">今日已施 · 今日 {{ awaken.todayGifts }} 条</template>
              <template v-else>今日<b>尚未随施</b></template>
            </div>
            <div>本月随施 {{ personal.monthSuishi || 0 }} 条</div>
          </div>
          <div class="aw-foot">
            <span :class="awaken.todayGifts > 0 ? 'done-chip' : 'todo-chip'" title="口径：今天已发布随施内容就算完成">{{ awaken.todayGifts > 0 ? '今日完成' : '今日待施' }}</span>
            <span class="spacer" />
            <el-button :type="awaken.todayGifts > 0 ? 'default' : 'primary'" size="small" @click="openPublish('suishi')">{{ awaken.todayGifts > 0 ? '再去随施' : '去随施' }}</el-button>
          </div>
        </article>

        <article class="aw-card">
          <div class="aw-head"><span class="aw-icon"><ElIcon><Reading /></ElIcon></span>再读一本</div>
          <div class="aw-stat">
            <div>按章节精读同一本书，累计进度，形成自己的书摘。</div>
            <div>
              <template v-if="awakenBook">当前：<b>《{{ awakenBook.title }}》</b>{{ positionText(awakenBook) }}</template>
              <template v-else>还没有在读的书</template>
            </div>
            <div>在读 {{ awaken.rereadingBooks || 0 }} 本 · 本月再读 {{ personal.monthZaidu || 0 }} 条</div>
          </div>
          <div class="aw-foot">
            <span :class="awakenBook ? 'done-chip' : 'todo-chip'" title="口径：书架里有「在读」的书就算精读中">{{ awakenBook ? '今日完成' : '未开始' }}</span>
            <span class="spacer" />
            <el-button v-if="awakenBook" type="success" size="small" @click="openPublish('zaidu')">继续精读</el-button>
            <el-button v-else type="primary" size="small" @click="goLibrary">去选书</el-button>
          </div>
        </article>
      </div>

      <!-- 第二行：左边 书架小表 + 阅读日历，右边 提醒卡 + 我的本月 -->
      <div class="aw-layout">
        <div class="aw-col">
          <section class="aw-card-box aw-shelf">
            <div class="panel-title">我的书架（在读 / 已读完 / 收藏）<button type="button" class="corner-link" @click="active = 'shelf'">进入书架管理 ›</button></div>
            <!-- 列里没有数据的格子留空（比如没挂电子书、没有阅读进度），不编 -->
            <table v-if="glanceRows.length" class="aw-tbl">
              <thead>
                <tr><th>书目</th><th>最近</th><th>进度</th><th class="num">页码</th><th>操作</th></tr>
              </thead>
              <tbody>
                <tr v-for="book in glanceRows" :key="book.id">
                  <td class="em">《{{ book.title }}》<template v-if="book.author"> · {{ book.author }}</template></td>
                  <td :title="book.status === 'finished' ? '读完的时间' : '书架记录最近更新的时间'">{{ recentText(book) }}</td>
                  <td>
                    <div class="rb"><i :style="{ width: pctOf(book) + '%' }" /></div>
                    <span class="sub2">{{ progressLabel(book) }}</span>
                  </td>
                  <td class="num">{{ pageLabel(book) }}</td>
                  <td class="aw-acts">
                    <button v-if="book.pdfFileId" type="button" class="text-btn" @click="openReader(book)">{{ book.status === 'finished' ? '回顾' : '继续读' }}</button>
                    <button type="button" class="text-btn" @click="openBookFeed(book)">动态</button>
                  </td>
                </tr>
              </tbody>
            </table>
            <p v-else class="aw-empty">
              书架里还没有在读、读完或收藏的书。<button type="button" class="text-btn" @click="goLibrary">去书目浏览 ›</button>
            </p>
          </section>

          <section class="aw-card-box aw-cal">
            <div class="panel-title">阅读记录 · {{ currentMonthKey }}（已读 {{ monthReadCount }}/{{ monthElapsed }} 天）<button type="button" class="corner-link" @click="active = 'records'">查看阅读记录 ›</button></div>
            <div class="cal-grid">
              <span v-for="h in CAL_WEEK" :key="`h-${h}`" class="cal-cell hd">{{ h }}</span>
              <span
                v-for="cell in calCells"
                :key="cell.key"
                class="cal-cell"
                :class="{ blank: cell.blank, done: cell.done, today: cell.today }"
              >{{ cell.day || '' }}</span>
            </div>
          </section>
        </div>

        <div class="aw-col">
          <section class="aw-card-box aw-remind">
            <div class="panel-title">每日提醒 · 闹钟式<button type="button" class="corner-link" @click="active = 'settings'">提醒设置 ›</button></div>
            <!-- 三个控件改的都是同一份提醒设置（与“提醒设置”页签共用），改了即刻保存 -->
            <div class="set-row">
              <span>提醒开关</span>
              <el-switch v-model="settings.reminderEnabled" :active-value="1" :inactive-value="0" @change="saveNow" />
            </div>
            <div class="set-row">
              <span>提醒时间</span>
              <el-time-picker v-model="settings.remindTime" format="HH:mm" value-format="HH:mm" placeholder="20:00" size="small" class="remind-time" @change="saveNow" />
            </div>
            <div class="set-row">
              <span>频率</span>
              <span class="chip-row">
                <button v-for="f in FREQ_OPTIONS" :key="f.value" type="button" class="ver-chip" :class="{ active: settings.freqCode === f.value }" @click="setFreq(f.value)">{{ f.label }}</button>
              </span>
            </div>
            <p class="set-note">完全自愿、非强制（当日不传也可以），改动即时保存。微信订阅消息授权与降级策略为小程序端能力；PC 仅保存个人提醒设置。</p>
          </section>

          <section class="aw-card-box aw-month">
            <!-- 月份与“共几条”并到标题行：这张卡在首屏底部，少一行就少 20px -->
            <div class="panel-title">我的本月（诵读 {{ personal.monthSongdu || 0 }} / 随施 {{ personal.monthSuishi || 0 }} / 再读 {{ personal.monthZaidu || 0 }}）<span class="spacer" /><span class="title-sub">{{ currentMonthLabel }} · 共 {{ personal.monthPosts || 0 }} 条</span></div>
            <!-- 获木鱼是累计数（接口没有按月的），所以标了“累计”；
                 原型还有“收评论”“被复诵”：个人统计接口里没有（只有我发出的评论数），不放 -->
            <div class="side-row"><span>获木鱼（累计）</span><b>{{ personal.myLikesReceived || 0 }}</b></div>
            <div class="side-row"><span>本月读完</span><b>{{ awaken.finishedThisMonth || 0 }}<i>本</i></b></div>
            <div class="side-row"><button type="button" class="hl-link" @click="router.push('/fabushi/highlights')">我的书摘 {{ highlightCount }} 条 ›</button></div>
            <div class="side-row">
              <span>已读天数</span>
              <span class="row-right">
                <b>{{ monthReadCount }}<i>天</i></b>
                <span class="sub2">累计 {{ awaken.totalReadDays || 0 }} 天 · 连续 {{ personal.myStreakDays || 0 }} 天</span>
              </span>
            </div>
            <div class="hbar-row">
              <span class="hbar-lbl">本月已读</span>
              <div class="hbar-track"><i :style="{ width: monthReadPct + '%' }" /></div>
              <span class="hbar-val">{{ monthReadCount }}/{{ monthElapsed }}</span>
            </div>
          </section>
        </div>
      </div>
    </section>

    <!-- ============ 我的书架（原型 P9B） ============ -->
    <section v-else-if="active === 'shelf'" class="shelf" v-loading="loading">
      <!-- 继续阅读条：默认是后端认定的“当前在读”；“换一本”在其余在读的书之间轮换 -->
      <div v-if="heroBook && heroBook.bookId" class="shelf-hero">
        <div class="book-spine">{{ spineOf(heroBook) }}</div>
        <div class="shelf-main">
          <div class="shelf-kicker">继续阅读</div>
          <div class="shelf-bookname">《{{ heroBook.title }}》<template v-if="heroBook.author"> · {{ heroBook.author }}</template></div>
          <div class="shelf-sub">{{ shelfStatusLabel(heroBook.status) }} · 读到第 {{ lastOf(heroBook) }}<template v-if="heroTotal"> / {{ heroTotal }}</template> 页</div>
          <div class="rb hero-rb"><i :style="{ width: pctOf(heroBook) + '%' }" /></div>
        </div>
        <div class="shelf-acts">
          <el-button v-if="heroBook.pdfFileId" type="primary" @click="openReader(heroBook)">继续阅读</el-button>
          <el-button v-if="readingBooks.length > 1" size="small" plain @click="switchHero">换一本</el-button>
        </div>
      </div>
      <div v-else class="shelf-hero shelf-hero-empty">
        <div class="book-spine spine-empty">书</div>
        <div class="shelf-main">
          <div class="shelf-kicker">继续阅读</div>
          <div class="shelf-bookname">书架里还没有在读的书</div>
          <div class="shelf-sub">去书目浏览挑一本加入书架，就能在这里接着读。</div>
        </div>
        <div class="shelf-acts"><el-button type="primary" @click="goLibrary">去书目浏览</el-button></div>
      </div>

      <!-- 筛选条 -->
      <div class="filter-card">
        <span class="fl">状态</span>
        <button
          v-for="s in SHELF_STATUS_OPTIONS"
          :key="s.value"
          type="button"
          class="ver-chip"
          :class="{ active: statusFilter === s.value }"
          @click="statusFilter = s.value"
        >{{ s.label }} {{ statusCounts[s.value] || 0 }}</button>
        <span class="fl">分组</span>
        <el-select v-model="groupFilter" style="width: 112px">
          <el-option v-for="g in groupOptions" :key="g.key" :label="g.label" :value="g.key" />
        </el-select>
        <span class="fl">排序</span>
        <el-select v-model="sortMode" style="width: 100px">
          <el-option v-for="s in SHELF_SORT_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <span class="seg" role="group" aria-label="视图">
          <button type="button" class="seg-item" :class="{ active: viewMode === 'grid' }" @click="viewMode = 'grid'">封面</button>
          <button type="button" class="seg-item" :class="{ active: viewMode === 'list' }" @click="viewMode = 'list'">列表</button>
        </span>
        <!-- 搜索和“批量管理”成组靠右：窄屏下整组换到第二行，不会只剩一个按钮掉下去。
             “批量管理 / 完成管理”两个状态一样宽（文字照原型）：进出管理模式时这一行不跳 -->
        <span class="filter-right">
          <el-input v-model="keyword" placeholder="书架内搜索" clearable style="width: 130px">
            <template #prefix><ElIcon><Search /></ElIcon></template>
          </el-input>
          <el-button class="manage-btn" :type="manageMode ? 'primary' : 'default'" @click="toggleManage">{{ manageMode ? '完成管理' : '批量管理' }}</el-button>
        </span>
      </div>

      <!-- 管理模式的批量操作条：没选书时按钮是灰的 -->
      <div v-if="manageMode" class="batch-bar">
        <b>已选 {{ selected.length }} 本</b>
        <el-button type="primary" size="small" :disabled="!selected.length" @click="batch('pin')">置顶</el-button>
        <el-button size="small" :disabled="!selected.length" @click="batch('unpin')">取消置顶</el-button>
        <el-button size="small" :disabled="!selected.length" title="移出后阅读进度与上传内容保留，可随时再加回" @click="batch('remove')">移出书架</el-button>
        <el-select v-model="batchGroupId" placeholder="选择分组" size="small" style="width: 118px">
          <el-option v-for="g in customGroups" :key="g.id" :label="g.name" :value="g.id" />
        </el-select>
        <el-button size="small" :disabled="!selected.length || batchGroupId == null" @click="batch('group')">加入分组</el-button>
        <span class="spacer" />
        <span class="batch-hint">点封面多选；每本书下面的按钮可以单独操作</span>
        <el-button size="small" :disabled="!selected.length" @click="selected = []">取消</el-button>
      </div>

      <div class="shelf-body">
        <!-- 封面网格 -->
        <section v-if="viewMode === 'grid'" class="shelf-grid">
          <div
            v-for="book in pagedShelf"
            :key="book.id"
            class="scell"
            :class="{ checked: selected.includes(book.id) }"
            role="checkbox"
            :tabindex="manageMode ? 0 : -1"
            :aria-checked="selected.includes(book.id)"
            :aria-label="`《${book.title}》`"
            :title="book.title"
            @click="onCardClick(book)"
            @keydown.self.enter.prevent="onCardClick(book)"
            @keydown.self.space.prevent="onCardClick(book)"
          >
            <span v-if="book.pinned" class="spintop">顶</span>
            <span v-if="manageMode" class="scell-check" :class="{ on: selected.includes(book.id) }"><ElIcon><Check /></ElIcon></span>
            <div class="book-spine">{{ spineOf(book) }}</div>
            <div class="sname">《{{ book.title }}》<span v-if="isNewBook(book)" class="badge-new">NEW</span></div>
            <div class="sprog" :class="{ 'is-done': book.status === 'finished' }">
              {{ cellText(book) }}<ElIcon v-if="book.status === 'finished'" class="done-icon"><Check /></ElIcon>
            </div>
            <div class="rb" :class="{ 'rb-done': book.status === 'finished' }"><i :style="{ width: pctOf(book) + '%' }" /></div>
            <!-- 管理模式：书名、进度条各占一行，操作按钮另起一行放在最下面，宽了会换行，不会挤到书名。
                 契约见 views/fabushi/fabushi-shelf-manage.spec.js -->
            <div v-if="manageMode" class="scell-acts" @click.stop>
              <el-select :model-value="book.status" size="small" class="scell-status" @change="(v) => changeStatus(book, v)">
                <el-option v-for="s in SHELF_STATUS_OPTIONS.filter((x) => x.value !== 'all')" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
              <div class="scell-btns">
                <button type="button" class="mini-btn" :title="book.pinned ? '取消置顶' : '置顶'" :aria-label="`《${book.title}》${book.pinned ? '取消置顶' : '置顶'}`" @click="togglePin(book)"><ElIcon><Top /></ElIcon></button>
                <button type="button" class="mini-btn" title="上移" :aria-label="`《${book.title}》上移`" @click="move(book, -1)"><ElIcon><ArrowUp /></ElIcon></button>
                <button type="button" class="mini-btn" title="下移" :aria-label="`《${book.title}》下移`" @click="move(book, 1)"><ElIcon><ArrowDown /></ElIcon></button>
                <button type="button" class="mini-btn danger" title="移出书架" :aria-label="`《${book.title}》移出书架`" @click="remove(book)"><ElIcon><Close /></ElIcon></button>
              </div>
            </div>
          </div>
          <div v-if="!visibleShelf.length" class="grid-empty">
            当前筛选下没有书架记录，<button type="button" class="text-btn" @click="goLibrary">去书目浏览</button>加入一本吧。
          </div>
          <div v-if="shelfTotalPages > 1" class="pager grid-pager">
            <span>共 {{ visibleShelf.length }} 本</span>
            <el-pagination v-model:current-page="shelfPage" :page-size="shelfSize" :total="visibleShelf.length" layout="prev, pager, next" small />
          </div>
        </section>

        <!-- 列表视图 -->
        <section v-else class="shelf-list">
          <div
            v-for="book in pagedShelf"
            :key="book.id"
            class="srow"
            :class="{ checked: selected.includes(book.id), managing: manageMode }"
            @click="onCardClick(book)"
          >
            <span v-if="manageMode" class="srow-pick" :class="{ on: selected.includes(book.id) }"><ElIcon><Check /></ElIcon></span>
            <div class="book-spine">{{ spineOf(book) }}</div>
            <div class="srow-main">
              <div class="srow-title">《{{ book.title }}》<span class="tag" :class="STATUS_TONE[book.status]">{{ shelfStatusLabel(book.status) }}</span><span v-if="book.pinned" class="tag orange">置顶</span><span v-if="isNewBook(book)" class="badge-new">NEW</span></div>
              <div class="srow-meta">{{ book.author || '—' }}<template v-if="book.pages"> · 共 {{ book.pages }} 页</template> · 读到第 {{ lastOf(book) }} 页</div>
            </div>
            <div class="srow-prog">
              <div class="rb"><i :style="{ width: pctOf(book) + '%' }" /></div>
              <span class="sprog">{{ progressText(book) }}</span>
            </div>
            <!-- 管理模式下操作区另起一行：书名和进度列不被挤短。契约见 views/fabushi/fabushi-shelf-manage.spec.js -->
            <div class="srow-acts" @click.stop>
              <el-button v-if="!manageMode && book.pdfFileId" link type="primary" size="small" @click="openReader(book)">{{ book.status === 'finished' ? '回顾' : '续读' }}</el-button>
              <template v-if="manageMode">
                <el-select :model-value="book.status" size="small" style="width: 92px" @change="(v) => changeStatus(book, v)">
                  <el-option v-for="s in SHELF_STATUS_OPTIONS.filter((x) => x.value !== 'all')" :key="s.value" :label="s.label" :value="s.value" />
                </el-select>
                <el-button link size="small" @click="togglePin(book)">{{ book.pinned ? '取消置顶' : '置顶' }}</el-button>
                <el-button link size="small" @click="move(book, -1)">上移</el-button>
                <el-button link size="small" @click="move(book, 1)">下移</el-button>
                <el-button link type="danger" size="small" @click="remove(book)">移出</el-button>
              </template>
            </div>
          </div>
          <div v-if="!visibleShelf.length" class="list-empty">
            当前筛选下没有书架记录，<button type="button" class="text-btn" @click="goLibrary">去书目浏览</button>加入一本吧。
          </div>
          <div v-if="shelfTotalPages > 1" class="pager list-pager">
            <span>共 {{ visibleShelf.length }} 本</span>
            <el-pagination v-model:current-page="shelfPage" :page-size="shelfSize" :total="visibleShelf.length" layout="prev, pager, next" small />
          </div>
        </section>

        <!-- 右栏：书架统计 + 分组管理 -->
        <aside class="side-col">
          <section class="side-card">
            <div class="side-title">书架统计<template v-if="userStore.realName"> · {{ userStore.realName }}</template></div>
            <div class="side-row"><span>想读 / 在读 / 已读完 / 收藏</span><b>{{ shelfStatCounts.wish }} / {{ shelfStatCounts.reading }} / {{ shelfStatCounts.finished }} / {{ shelfStatCounts.favorite }}</b></div>
            <div class="side-row"><span>本月读完</span><b>{{ summary.finishedThisMonth || 0 }}<i>本</i></b></div>
            <div class="side-row"><span>累计阅读天数</span><b>{{ awaken.totalReadDays || 0 }}<i>天</i></b></div>
            <div class="side-row"><span>当前精读</span><b class="side-book">{{ currentTitle ? `《${currentTitle}》` : '—' }}</b></div>
          </section>
          <section class="side-card">
            <div class="side-title">分组管理</div>
            <!-- 自动分组是按书目类型来的，可能有十来个：列表限高、内部滚动，别把“新建分组”顶出首屏 -->
            <div class="group-rows">
              <button
                v-for="g in groupOptions"
                :key="g.key"
                type="button"
                class="sub-row"
                :class="{ active: String(groupFilter) === String(g.key) }"
                @click="groupFilter = g.key"
              >
                <span class="sub-name"><template v-if="g.type === 'all'">全部书目</template><template v-else>{{ g.label }}</template><template v-if="g.type === 'custom'">（自建）</template></span>
                <span class="cnt">{{ g.count || 0 }}</span>
              </button>
            </div>
            <div class="rec-acts"><el-button plain size="small" @click="groupDialogVisible = true">＋ 新建分组</el-button></div>
            <p class="side-note">自动分组按书目类型归类；自建分组可以新建、改名、删除。</p>
          </section>
        </aside>
      </div>
    </section>

    <!-- ============ 阅读记录 ============ -->
    <section v-else-if="active === 'records'" class="panel panel-pad records-card">
      <div class="panel-title">
        阅读记录<span class="tt-sub">· 每天读过几次</span>
        <span class="spacer" />
        <el-input v-model="month" placeholder="YYYY-MM" style="width: 130px" @change="loadRecords" />
      </div>
      <div class="rec-kpis">
        <div class="rec-kpi"><span>本月阅读</span><b>{{ records.readDays || 0 }}</b><span>天</span></div>
        <div class="rec-kpi"><span>阅读</span><b>{{ records.readCount || 0 }}</b><span>次</span></div>
      </div>
      <div class="rec-list">
        <div v-for="day in pagedRecordDays" :key="day.date" class="record-row"><span>{{ day.date }}</span><b>{{ day.count || 0 }} 次</b></div>
        <div v-if="!recordDays.length" class="empty">本月暂无阅读记录</div>
        <div v-if="recordTotalPages > 1" class="rec-pager">
          <el-pagination v-model:current-page="recordPage" :page-size="recordSize" :total="recordDays.length" layout="prev, pager, next" small />
        </div>
      </div>
    </section>

    <!-- ============ 提醒设置 ============ -->
    <section v-else class="panel panel-pad settings-card">
      <div class="panel-title">阅读提醒</div>
      <el-form label-width="120px" class="settings-form">
        <el-form-item label="缺卡提醒"><el-switch v-model="settings.reminderEnabled" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="频率">
          <el-select v-model="settings.freqCode" style="width: 180px">
            <el-option v-for="f in FREQ_OPTIONS" :key="f.value" :label="f.label" :value="f.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="提醒时间"><el-time-picker v-model="settings.remindTime" format="HH:mm" value-format="HH:mm" placeholder="20:00" /></el-form-item>
        <el-form-item label="木鱼音效"><el-switch v-model="settings.soundEnabled" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item><el-button type="primary" :loading="saving" @click="saveSettings">保存设置</el-button></el-form-item>
      </el-form>
      <p class="hint">微信订阅消息授权与降级策略为小程序端能力；PC 仅保存个人提醒设置。</p>
    </section>

    <!-- 分组的新建 / 改名 / 删除 -->
    <el-dialog v-model="groupDialogVisible" title="书架分组管理" width="460px" class="fx-dialog">
      <div class="group-manage-list">
        <div v-for="g in customGroups" :key="g.id" class="group-manage-row">
          <span>{{ g.name }}（{{ g.bookCount || 0 }} 本）</span>
          <span class="group-manage-acts">
            <el-button link size="small" @click="renameGroup(g)">改名</el-button>
            <el-button link type="danger" size="small" @click="removeGroup(g)">删除</el-button>
          </span>
        </div>
        <div v-if="!customGroups.length" class="empty">还没有自建分组</div>
      </div>
      <div class="group-create">
        <el-input v-model="newGroupName" placeholder="新建分组名（如：案头常读）" maxlength="64" />
        <el-button type="primary" @click="createGroup">新建</el-button>
      </div>
      <template #footer><div class="dialog-footer"><el-button @click="groupDialogVisible = false">关闭</el-button></div></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElIcon } from 'element-plus'
import { ArrowDown, ArrowUp, ChatLineRound, Check, Close, Reading, Search, Sunrise, Top } from '@element-plus/icons-vue'
import {
  getFabushiShelf, getFabushiAwakenStatus, updateFabushiShelf, batchFabushiShelf,
  removeFabushiShelf, getFabushiRecords, getFabushiSettings, saveFabushiSettings,
  getFabushiShelfGroups, createFabushiShelfGroup, renameFabushiShelfGroup, deleteFabushiShelfGroup,
  getFabushiPersonalStats, getMyFabushiBookmarks,
} from '@/api/fabushi'
import {
  SHELF_STATUS_OPTIONS, SHELF_SORT_OPTIONS, shelfStatusLabel, progressPct, filterShelf, sortShelf,
} from '@/utils/fabushi-shelf'
import { usePagedList } from '@/composables/usePagedList'
import { useUserStore } from '@/store/user'
import '@/assets/fabushi-v8.css'

const router = useRouter()
const userStore = useUserStore()
const tabs = [
  { value: 'awaken', label: '觉醒总览' },
  { value: 'shelf', label: '我的书架' },
  { value: 'records', label: '阅读记录' },
  { value: 'settings', label: '提醒设置' }
]
const active = ref('awaken')
const loading = ref(false)
const saving = ref(false)
const shelf = ref(/** @type {any[]} */ ([]))
const groups = ref(/** @type {any[]} */ ([]))
const summary = ref({ total: 0, finishedThisMonth: 0, currentBook: null })
// 初值把下面模板里读到的键都带上，否则 TS 按字面量收窄后会报“属性不存在”
const awaken = ref({ todayRecitations: 0, todayGifts: 0, rereadingBooks: 0, finishedThisMonth: 0, totalReadDays: 0, currentBook: null })
// 【PROTO-AUDIT-20261123 P9】我的本月（三类型拆分）+ 我的书摘计数
const personal = ref({ monthSongdu: 0, monthSuishi: 0, monthZaidu: 0, monthPosts: 0, myLikesReceived: 0, myStreakDays: 0 })
/** 【P9B 2026-11-23】只取条数用于按钮徽标，书摘列表在独立页 /fabushi/highlights */
const highlightCount = ref(0)
const currentMonthLabel = computed(() => {
  const d = new Date()
  return `${d.getFullYear()} 年 ${d.getMonth() + 1} 月`
})
const loadHighlightCount = async () => {
  try {
    const { data } = await getMyFabushiBookmarks()
    highlightCount.value = (data || []).length
  } catch (e) {
    highlightCount.value = 0
  }
}
// 日期键一律按本机日期算（toISOString 是 UTC：月初的深夜、月末的傍晚会和本机差一天，甚至差一个月）
const pad2 = (n) => String(n).padStart(2, '0')
const dateKeyOf = (d) => `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
// 阅读记录页签默认看本机当下的月份
const month = ref(dateKeyOf(new Date()).slice(0, 7))
const records = ref({ days: [], readDays: 0, readCount: 0 })
const settings = reactive({ reminderEnabled: 0, freqCode: 'daily', remindTime: '20:00', soundEnabled: 1 })
const statusFilter = ref('all')
const groupFilter = ref('all')
const sortMode = ref('recent')
const keyword = ref('')
const viewMode = ref('grid')
const manageMode = ref(false)
const selected = ref([])
const batchGroupId = ref(null)
const groupDialogVisible = ref(false)
const newGroupName = ref('')

const groupOptions = computed(() => {
  const all = [{ key: 'all', value: 'all', label: '全部分组', type: 'all', count: shelf.value.length }]
  return all.concat(groups.value.map((g) => ({
    key: g.id,
    value: g.id,
    label: g.name,
    type: g.groupType,
    categoryCode: g.categoryCode,
    count: g.bookCount || 0
  })))
})
const customGroups = computed(() => groups.value.filter((g) => g.groupType === 'custom'))
const currentGroup = computed(() => groupOptions.value.find((g) => String(g.key) === String(groupFilter.value)) || null)
const visibleShelf = computed(() => sortShelf(
  filterShelf(shelf.value, { status: statusFilter.value, keyword: keyword.value, group: currentGroup.value }),
  sortMode.value
))
const { current: shelfPage, size: shelfSize, totalPages: shelfTotalPages, paged: pagedShelf, reset: resetShelfPage } = usePagedList(visibleShelf, { pageSize: 12 })
const recordDays = computed(() => records.value.days || [])
const { current: recordPage, size: recordSize, totalPages: recordTotalPages, paged: pagedRecordDays } = usePagedList(recordDays, { pageSize: 10 })
watch([statusFilter, groupFilter, keyword, sortMode, viewMode], resetShelfPage)

const loadShelf = async () => {
  loading.value = true
  try {
    const { data } = await getFabushiShelf()
    shelf.value = (data && data.records) || []
    groups.value = (data && data.groups) || []
    summary.value = (data && data.summary) || { total: 0, finishedThisMonth: 0, currentBook: null }
  } finally { loading.value = false }
}
const loadAwaken = async () => {
  const { data } = await getFabushiAwakenStatus()
  awaken.value = data || {}
}
const loadPersonal = async () => {
  try {
    const { data } = await getFabushiPersonalStats()
    personal.value = data || { monthSongdu: 0, monthSuishi: 0, monthZaidu: 0, monthPosts: 0, myLikesReceived: 0, myStreakDays: 0 }
  } catch (e) {
    personal.value = { monthSongdu: 0, monthSuishi: 0, monthZaidu: 0, monthPosts: 0, myLikesReceived: 0, myStreakDays: 0 }
  }
}
const loadRecords = async () => {
  const { data } = await getFabushiRecords(month.value)
  records.value = data || { days: [], readDays: 0, readCount: 0 }
}
// 觉醒总览的日历固定看当月，不跟着“阅读记录”页签里改的月份走；拉不到就当整月没读，不挡页面
const monthRecords = ref({ days: [], readDays: 0, readCount: 0 })
const loadMonthRecords = async () => {
  try {
    const { data } = await getFabushiRecords(currentMonthKey.value)
    monthRecords.value = data || { days: [], readDays: 0, readCount: 0 }
  } catch (e) {
    monthRecords.value = { days: [], readDays: 0, readCount: 0 }
  }
}
const loadSettings = async () => {
  const { data } = await getFabushiSettings()
  Object.assign(settings, data || {})
}
const loadGroups = async () => {
  const { data } = await getFabushiShelfGroups()
  groups.value = data || []
}

const openPublish = (scope) => router.push(`/fabushi/feed?scope=${scope}`)
const goLibrary = () => router.push('/fabushi/library')
const openReader = (book) => { if (book && book.bookId) router.push(`/fabushi/read/${book.bookId}`) }
/** 小表里的“动态”：去认知广场的按书浏览看这本书下大家上传的内容。
 *  认知广场带着 bookId 进去时页面自身会弹发布框，这里只是去看动态，所以带 publish=0 */
const openBookFeed = (book) => router.push({ path: '/fabushi/feed', query: { scope: 'zaidu', bookId: book.bookId, publish: '0' } })
const toggleManage = () => { manageMode.value = !manageMode.value; selected.value = [] }
const onCardClick = (book) => {
  if (manageMode.value) toggleSelected(book.id)
  else if (book.pdfFileId) router.push(`/fabushi/read/${book.bookId}`)
}
const toggleSelected = (id) => {
  selected.value = selected.value.includes(id) ? selected.value.filter((x) => x !== id) : [...selected.value, id]
}
const changeStatus = async (book, status) => {
  await updateFabushiShelf(book.id, { status })
  book.status = status
  ElMessage.success('已更新阅读状态')
}
const togglePin = async (book) => {
  await updateFabushiShelf(book.id, { pinned: book.pinned ? 0 : 1 })
  book.pinned = book.pinned ? 0 : 1
  ElMessage.success(book.pinned ? '已置顶' : '已取消置顶')
}
const move = async (book, delta) => {
  const ids = visibleShelf.value.map((x) => x.id)
  const index = ids.indexOf(book.id)
  const target = index + delta
  if (target < 0 || target >= ids.length) return
  ;[ids[index], ids[target]] = [ids[target], ids[index]]
  await batchFabushiShelf({ ids, action: 'reorder' })
  await loadShelf()
}
const batch = async (action) => {
  await batchFabushiShelf({ ids: selected.value, action, groupId: action === 'group' ? batchGroupId.value : undefined })
  selected.value = []
  ElMessage.success('批量操作完成')
  await loadShelf()
}
const remove = async (book) => {
  await removeFabushiShelf(book.id)
  ElMessage.success('已移出书架，阅读进度与上传内容保留')
  await loadShelf()
}
const createGroup = async () => {
  if (!newGroupName.value.trim()) { ElMessage.warning('请输入分组名'); return }
  await createFabushiShelfGroup(newGroupName.value.trim())
  newGroupName.value = ''
  await Promise.all([loadGroups(), loadShelf()])
}
const renameGroup = async (g) => {
  try {
    const { value } = await ElMessageBox.prompt('新分组名', '重命名分组', { inputValue: g.name, inputValidator: (v) => Boolean(v && v.trim()) || '分组名不能为空' })
    await renameFabushiShelfGroup(g.id, value.trim())
    await Promise.all([loadGroups(), loadShelf()])
  } catch (e) { if (e !== 'cancel' && e !== 'close') throw e }
}
const removeGroup = async (g) => {
  try {
    await ElMessageBox.confirm(`删除「${g.name}」？书架中的书会回到未分组，阅读进度不受影响。`, '确认删除', { type: 'warning' })
    await deleteFabushiShelfGroup(g.id)
    await Promise.all([loadGroups(), loadShelf()])
  } catch (e) { if (e !== 'cancel' && e !== 'close') throw e }
}
const saveSettings = async () => {
  saving.value = true
  try { await saveFabushiSettings({ ...settings }); ElMessage.success('设置已保存') } finally { saving.value = false }
}
// 觉醒总览的提醒卡：改了就保存（同一个 saveSettings，同一个接口）
const saveNow = () => saveSettings()
const setFreq = (code) => {
  if (settings.freqCode === code) return undefined
  settings.freqCode = code
  return saveSettings()
}

/* ---------------- 按原型 v8 的展示用小函数与派生数据（不涉及接口） ---------------- */
const FREQ_OPTIONS = [
  { value: 'daily', label: '每天' }, { value: 'weekday', label: '工作日' }, { value: 'weekend', label: '休息日' }
]
const CAL_WEEK = ['一', '二', '三', '四', '五', '六', '日']
const STATUS_TONE = { wish: 'gray', reading: 'blue', finished: 'green', favorite: 'orange' }

/** 觉醒三板块里“再读一本”的当前在读（与 feed.vue 右栏同一个口径：书架里有「在读」的书） */
const awakenBook = computed(() => (awaken.value.currentBook && awaken.value.currentBook.bookId ? awaken.value.currentBook : null))
const currentTitle = computed(() => (summary.value.currentBook && summary.value.currentBook.title) || (awakenBook.value && awakenBook.value.title) || '')
const totalOf = (book) => {
  if (book.totalPages != null) return Number(book.totalPages)
  return book.pages != null ? Number(book.pages) : 0
}
const lastOf = (book) => Number(book.lastPage != null ? book.lastPage : 1) || 1
/** 进度百分比：优先用接口给的 progressPct，没有再按页码算 */
const pctOf = (book) => {
  const pct = Number(book && book.progressPct)
  return Number.isFinite(pct) ? pct : progressPct(book)
}
const spineOf = (book) => String((book && (book.cover || book.title)) || '书').slice(0, 4)
const isNewBook = (book) => book.status === 'wish' && pctOf(book) === 0
const positionText = (book) => {
  const total = totalOf(book)
  if (total > 0) return ` · 第 ${lastOf(book)} / ${total} 页`
  return lastOf(book) > 1 ? ` · 第 ${lastOf(book)} 页` : ''
}

// ---- 我的书架小表（觉醒总览第二行左上）----
const GLANCE_STATUSES = ['reading', 'finished', 'favorite']
// 最多列 3 行：首屏里这张表和下面的阅读日历叠在一起，行数多了会把日历顶出视口；其余的去书架页签看
const GLANCE_MAX_ROWS = 3
const glanceRows = computed(() => {
  const current = summary.value.currentBook
  const rank = (book) => (current && current.bookId && book.id === current.id ? 0 : GLANCE_STATUSES.indexOf(book.status) + 1)
  return shelf.value
    .filter((book) => GLANCE_STATUSES.includes(book.status))
    .sort((a, b) => rank(a) - rank(b) || String(b.updatedAt || '').localeCompare(String(a.updatedAt || '')))
    .slice(0, GLANCE_MAX_ROWS)
})
const shortTime = (value) => {
  if (!value) return ''
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return ''
  const now = new Date()
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const startOfDay = new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
  const dayDiff = Math.round((startOfToday - startOfDay) / 86400000)
  const hm = `${pad2(d.getHours())}:${pad2(d.getMinutes())}`
  if (dayDiff === 0) return `今天 ${hm}`
  if (dayDiff === 1) return `昨天 ${hm}`
  return d.getFullYear() === now.getFullYear() ? `${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} ${hm}` : dateKeyOf(d)
}
/** “最近”：读完的写读完的日期；其余只有书架记录的更新时间（接口没有“最近阅读时间”），照实显示 */
const recentText = (book) => {
  if (book.status === 'finished' && book.finishedAt) {
    const d = new Date(book.finishedAt)
    return Number.isNaN(d.getTime()) ? '' : `${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} 读完`
  }
  return shortTime(book.updatedAt)
}
const progressLabel = (book) => {
  const total = book.totalPages != null ? Number(book.totalPages) : 0
  const pct = pctOf(book)
  if (total > 0) return `${lastOf(book)}/${total} 页`
  return pct > 0 ? `${pct}%` : ''
}
const pageLabel = (book) => {
  const pages = Number(book.pages)
  if (pages > 0) return `${lastOf(book)}/${pages} 页`
  return lastOf(book) > 1 ? `第 ${lastOf(book)} 页` : ''
}

// ---- 阅读日历（周一起始；读过的日子、今天各有标记；月份里的空位不补上个月的日子）----
const calendarCells = (monthKey, readDates, todayKey) => {
  const [year, monthNo] = String(monthKey || '').split('-').map(Number)
  if (!year || !monthNo) return []
  const lead = (new Date(year, monthNo - 1, 1).getDay() + 6) % 7
  const days = new Date(year, monthNo, 0).getDate()
  const cells = []
  for (let i = 0; i < lead; i += 1) cells.push({ key: `blank-${i}`, blank: true })
  for (let day = 1; day <= days; day += 1) {
    const key = `${year}-${pad2(monthNo)}-${pad2(day)}`
    cells.push({ key, day, done: readDates.has(key), today: key === todayKey })
  }
  return cells
}
const todayKey = computed(() => dateKeyOf(new Date()))
const currentMonthKey = computed(() => todayKey.value.slice(0, 7))
const readDateSet = computed(() => new Set((monthRecords.value.days || []).map((d) => d.date)))
const calCells = computed(() => calendarCells(currentMonthKey.value, readDateSet.value, todayKey.value))
const monthReadCount = computed(() => calCells.value.filter((cell) => cell.done).length)
/** 分母：当月到今天为止过了几天 */
const monthElapsed = computed(() => Number(todayKey.value.slice(8, 10)) || 1)
const monthReadPct = computed(() => (monthElapsed.value ? Math.round(monthReadCount.value * 100 / monthElapsed.value) : 0))

// ---- 书架的派生数据 ----
/** 状态分段上的条数：跟着分组和搜索变（先按分组 / 搜索筛，再数各状态） */
const statusCounts = computed(() => {
  const rows = filterShelf(shelf.value, { status: 'all', keyword: keyword.value, group: currentGroup.value })
  const out = { all: rows.length, wish: 0, reading: 0, finished: 0, favorite: 0 }
  rows.forEach((book) => { if (book.status in out) out[book.status] += 1 })
  return out
})
/** 右栏“书架统计”：整个书架的条数 */
const shelfStatCounts = computed(() => {
  const out = { wish: 0, reading: 0, finished: 0, favorite: 0 }
  shelf.value.forEach((book) => { if (book.status in out) out[book.status] += 1 })
  return out
})
const progressText = (book) => {
  if (book.status === 'finished') return '读完'
  const total = totalOf(book)
  const last = lastOf(book)
  const pct = pctOf(book)
  const label = shelfStatusLabel(book.status)
  // 还停在第 1 页、进度 0%：没有真实的阅读进度，不写“1/288 页”这种像已经开始的样子
  if (last <= 1 && pct <= 0) return book.status === 'reading' ? '在读 · 刚加入' : `${label} · 未开始`
  if (total > 0) {
    const pos = `${last}/${total} 页`
    return book.status === 'reading' ? (pct > 0 ? `${pos} · ${pct}%` : pos) : `${label} · ${pos}`
  }
  return `${label} · 第 ${last} 页`
}
/** 封面格子的进度文字：平时在有电子书的书后面带一句“续读 ›”，点格子就进阅读器；管理模式下点格子是勾选，就不带 */
const cellText = (book) => {
  const base = progressText(book)
  if (manageMode.value || !book.pdfFileId) return base
  return book.status === 'finished' ? `${base} · 回顾 ›` : `${base} · 续读 ›`
}
/** 继续阅读条：在读的书，后端认定的“当前在读”排第一 */
const readingBooks = computed(() => {
  const rows = shelf.value.filter((book) => book.status === 'reading')
  const current = summary.value.currentBook
  if (!current || !current.bookId) return rows
  return rows.filter((book) => book.id === current.id).concat(rows.filter((book) => book.id !== current.id))
})
const heroBookId = ref(null)
const heroBook = computed(() => readingBooks.value.find((book) => book.id === heroBookId.value) || readingBooks.value[0] || null)
const heroTotal = computed(() => (heroBook.value ? totalOf(heroBook.value) : 0))
const switchHero = () => {
  const list = readingBooks.value
  if (list.length < 2) return
  const index = list.findIndex((book) => book.id === heroBook.value.id)
  heroBookId.value = list[(index + 1) % list.length].id
}

onMounted(async () => {
  await Promise.all([loadShelf(), loadAwaken(), loadRecords(), loadSettings(), loadPersonal(), loadHighlightCount(), loadMonthRecords()])
})
</script>

<style scoped>
/* 通用件（页签 .cat-tabs、筛选条 .filter-card、标签 .tag / .ver-chip / .done-chip / .todo-chip / .badge-new / .seg、
   白卡 .panel、右栏 .side-col / .side-card / .side-title / .side-row / .sub-row、开关行 .set-row、日历 .cal-grid / .cal-cell、
   进度条 .rb、统计条 .hbar-*、书脊 .book-spine、继续阅读条 .shelf-hero / .shelf-*、封面格子 .scell、批量条 .batch-bar）
   在 assets/fabushi-v8.css；这里只写 P9 / P9B 自己的版式。数值取自原型。 */

/* ---------- 页签这一行 ---------- */
.tab-row { display: flex; align-items: center; flex-shrink: 0; gap: 10px; }
.tab-row .cat-tabs { flex: 1; }

/* ---------- 觉醒总览（原型 P9） ---------- */
.awaken { display: flex; flex-direction: column; gap: 14px; }
.aw-cards { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); flex-shrink: 0; gap: 14px; }
.aw-card { display: flex; flex-direction: column; padding: 14px 16px; border-radius: var(--fx-r-md); background: #fff; box-shadow: var(--shadow-card); text-align: left; }
.aw-head { display: flex; align-items: center; gap: 9px; color: var(--text-primary); font-size: 14.5px; font-weight: 800; }
.aw-icon { display: flex; align-items: center; justify-content: center; flex-shrink: 0; width: 32px; height: 32px; border-radius: 9px; background: var(--hero-gradient); color: #fff; font-size: 17px; }
/* 随施（琥珀）、再读（绿）两个图标底色是原型自己的渐变 */
.aw-card:nth-child(2) .aw-icon { background: linear-gradient(135deg, #b45309, #f59e0b); }
.aw-card:nth-child(3) .aw-icon { background: linear-gradient(135deg, #047857, #10b981); }
.aw-stat { margin-top: 9px; color: var(--text-secondary); font-size: 12px; line-height: 1.75; }
.aw-stat b { color: var(--text-primary); }
.aw-foot { display: flex; align-items: center; gap: 8px; margin-top: auto; padding-top: 10px; }

.aw-layout { display: grid; grid-template-columns: 1.25fr 1fr; gap: 14px; align-items: stretch; }
/* 两列里上下两张卡之间留 12px（原型 14px）：页签这一行占了高度，首屏底部要省着用 */
.aw-col { display: flex; flex-direction: column; min-width: 0; min-height: 0; gap: 12px; }
.aw-card-box { min-width: 0; padding: 13px 16px; border-radius: var(--fx-r-md); background: #fff; box-shadow: var(--shadow-card); }
.aw-shelf, .aw-month { display: flex; flex-direction: column; flex: 1; }
.corner-link { margin-left: auto; padding: 0; border: 0; background: transparent; color: var(--primary-color); font-family: inherit; font-size: 10.5px; font-weight: 700; white-space: nowrap; cursor: pointer; }
.corner-link:hover, .text-btn:hover, .hl-link:hover { text-decoration: underline; }

/* 我的书架小表 */
.aw-tbl { width: 100%; border-collapse: collapse; font-size: 12.5px; }
.aw-tbl th { padding: 8px 9px; border-bottom: 1px solid var(--border-light); background: var(--fx-table-head); color: var(--fx-pill-gray); font-size: 12px; font-weight: 600; text-align: left; white-space: nowrap; }
.aw-tbl td { padding: 8px 9px; border-bottom: 1px solid var(--border-light); color: var(--text-primary); font-size: 12px; white-space: nowrap; }
.aw-tbl tbody tr:last-child td { border-bottom: 0; }
.aw-tbl .num { text-align: right; font-variant-numeric: tabular-nums; }
.aw-tbl td.em { font-weight: 700; }
.aw-tbl td > .rb { width: 120px; min-width: 60px; }
.aw-tbl td > .sub2 { display: block; }
.aw-acts { white-space: nowrap; }
.text-btn { padding: 0; border: 0; background: transparent; color: var(--primary-color); font-family: inherit; font-size: 12.5px; white-space: nowrap; cursor: pointer; }
.aw-acts .text-btn + .text-btn { margin-left: 6px; }
.aw-empty { margin: 0; padding: 16px 0; color: var(--text-placeholder); font-size: 12px; }

/* 每日提醒卡 */
.chip-row { display: flex; gap: 5px; }
.remind-time { width: 108px; }
.set-note { margin: 8px 0 0; color: var(--text-placeholder); font-size: 11px; line-height: 1.7; }

/* 我的本月（在首屏底部：标题行右边带月份与条数，行高与原型的一样约 31px） */
.title-sub { color: var(--fx-pill-gray); font-size: 11px; font-weight: 400; white-space: nowrap; }
.aw-month .side-row { padding: 4px 0; }
.aw-month .side-row:nth-of-type(2) { border-top: 0; }
.row-right { display: flex; align-items: baseline; gap: 8px; white-space: nowrap; }
.row-right .sub2 { margin-top: 0; }
.hl-link { padding: 0; border: 0; background: transparent; color: var(--primary-color); font-family: inherit; font-size: 12.5px; font-weight: 700; cursor: pointer; }
.aw-month .hbar-row { margin-top: 6px; padding: 5px 0; }
.aw-month .hbar-val { min-width: 46px; text-align: right; }
/* “3 天”“1 本”：数字后的单位小一号、淡一点 */
.side-row b i { margin-left: 3px; color: var(--fx-pill-gray); font-size: 11px; font-style: normal; font-weight: 600; }

/* ---------- 我的书架（原型 P9B） ---------- */
.shelf { display: flex; flex-direction: column; gap: 10px; }
/* 筛选条右边：搜索 + 管理，成组靠右 */
.filter-right { display: inline-flex; align-items: center; flex-shrink: 0; gap: 10px; margin-left: auto; }
.filter-right .manage-btn { width: 88px; }
.hero-rb { margin-top: 8px; }
.spine-empty { background: var(--fx-track); color: var(--fx-pill-gray); box-shadow: none; }
.batch-hint { color: var(--text-placeholder); font-size: 11px; white-space: nowrap; }
/* 封面网格和右栏等高（原型两栏都是满高的） */
.shelf-body { display: flex; flex: 1; align-items: stretch; min-height: 0; gap: 10px; }
.shelf-body > .side-col { width: 280px; }
.shelf-body > .side-col .side-card:last-child { flex: 1 0 auto; }
.shelf-body .side-col .sub-row { width: 100%; padding-top: 4px; padding-bottom: 4px; text-align: left; font-family: inherit; }
/* 自动分组有十来个：行高压到约 27px，列表最多露出 6 行、内部滚动，让“＋ 新建分组”留在首屏里 */
.group-rows { max-height: 166px; overflow-y: auto; }
/* 共享样式给 .rec-acts 里的按钮设了 flex: 1（那边是两个按钮平分）；这里只有一个，按原型收成小按钮 */
.shelf-body .rec-acts .el-button { flex: 0 0 auto; }
.side-row b.side-book { font-size: 13px; }

.shelf-grid { display: grid; flex: 1; grid-template-columns: repeat(5, minmax(0, 1fr)); align-content: start; min-width: 0; padding: 18px 16px 14px; border-radius: var(--fx-r-md); background: #fff; box-shadow: var(--shadow-card); gap: 18px 10px; }
/* 置顶标记（右上角）与勾选标记（左上角） */
.spintop { position: absolute; top: -8px; right: 8px; z-index: 2; display: flex; align-items: center; justify-content: center; width: 20px; height: 20px; border-radius: 50%; background: var(--fx-amber); color: #fff; font-size: 11px; font-weight: 800; box-shadow: 0 2px 6px rgba(180, 83, 9, .4); }
.scell-check { position: absolute; top: -8px; left: 8px; z-index: 2; display: flex; align-items: center; justify-content: center; width: 20px; height: 20px; border: 2px solid #fff; border-radius: 50%; background: var(--fx-track); color: transparent; font-size: 12px; box-shadow: 0 1px 4px rgba(16, 24, 40, .18); }
.scell-check.on { background: var(--primary-color); color: #fff; }
.scell:focus-visible { outline: 2px solid var(--primary-color); outline-offset: 4px; border-radius: 8px; }
.scell .sprog { display: flex; align-items: center; justify-content: center; gap: 3px; white-space: nowrap; }
.scell .sprog.is-done { color: var(--fx-green); font-weight: 700; }
.scell .sprog .done-icon { font-size: 11px; }
.scell .rb-done i { background: var(--fx-green); }

/* 管理模式：书名、进度条各占一行；操作区另起一行放在最下面。
   格子竖着排（块级，不是横向弹性容器）；状态下拉占满一行；四个图标按钮各自宽度固定（不收缩）、放不下就换行 ——
   书名永远不和按钮挤同一行。契约见 views/fabushi/fabushi-shelf-manage.spec.js */
.scell { display: block; }
.scell-acts { display: block; margin-top: 8px; }
.scell-status { width: 100%; }
.scell-btns { display: flex; flex-wrap: wrap; justify-content: center; gap: 4px; margin-top: 6px; }
.mini-btn { display: inline-flex; flex: 0 0 auto; align-items: center; justify-content: center; width: 27px; height: 24px; padding: 0; border: 1px solid var(--border-light); border-radius: 6px; background: #fff; color: var(--text-secondary); font-size: 13px; cursor: pointer; transition: border-color .15s, color .15s; }
.mini-btn:hover { border-color: var(--primary-color); color: var(--primary-color); }
.mini-btn.danger:hover { border-color: var(--el-color-danger); color: var(--el-color-danger); }
.grid-empty, .list-empty { padding: 28px 0; color: var(--text-placeholder); font-size: 12.5px; text-align: center; }
.grid-empty, .grid-pager { grid-column: 1 / -1; }
.grid-pager, .list-pager { margin: 4px -16px 0; }

/* 列表视图 */
.shelf-list { flex: 1; min-width: 0; padding: 6px 16px; border-radius: var(--fx-r-md); background: #fff; box-shadow: var(--shadow-card); }
.srow { display: flex; align-items: center; padding: 10px 6px; border-bottom: 1px solid var(--fx-line-soft); border-radius: 8px; cursor: pointer; gap: 14px; }
/* 管理模式下这一行允许换行：操作区另起一行 */
.srow.managing { flex-wrap: wrap; }
.srow.checked { background: var(--el-color-primary-light-9); }
.srow:hover .srow-title { color: var(--primary-color); }
.srow-pick { display: flex; align-items: center; justify-content: center; flex: 0 0 auto; width: 20px; height: 20px; border: 2px solid var(--fx-line); border-radius: 50%; background: #fff; color: transparent; font-size: 12px; }
.srow-pick.on { border-color: var(--primary-color); background: var(--primary-color); color: #fff; }
.srow .book-spine { width: 40px; height: 54px; font-size: 10px; }
/* 书名在可以收缩的区域里（min-width: 0 + 省略号）；状态标签跟在书名后面 */
.srow-main { flex: 1; min-width: 0; }
.srow-title { display: flex; align-items: center; overflow: hidden; color: var(--text-primary); font-size: 13px; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; gap: 6px; }
.srow-meta { margin-top: 3px; overflow: hidden; color: var(--text-muted); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
/* 进度列定宽、行尾操作区留出最小宽度：各行的列上下对齐 */
.srow-prog { flex: 0 0 240px; min-width: 0; }
.srow-prog .sprog { display: block; margin-top: 4px; overflow: hidden; color: var(--text-muted); font-size: 10.5px; text-overflow: ellipsis; white-space: nowrap; }
/* 行尾的操作区：宽度不收缩、按钮多了会换行；管理模式下独占第二行、靠右，书名和进度列不被挤短 */
.srow-acts { display: flex; flex: 0 0 auto; flex-wrap: wrap; align-items: center; justify-content: flex-end; min-width: 64px; max-width: 100%; gap: 6px; }
.srow.managing .srow-acts { flex: 1 0 100%; margin-top: 2px; }
.srow-acts .el-button { margin-left: 0; }
.srow-acts .el-select + .el-button, .srow-acts .el-button + .el-button { margin-left: 0; }

/* ---------- 阅读记录 / 提醒设置 ---------- */
.records-card .tt-sub { color: var(--text-muted); font-size: 11.5px; font-weight: 400; }
.rec-kpis { display: flex; margin-bottom: 12px; gap: 10px; }
.rec-kpi { display: flex; flex: 1; align-items: baseline; padding: 10px 14px; border-radius: var(--fx-r-sm); background: var(--fx-table-head); gap: 6px; }
.rec-kpi b { color: var(--primary-color); font-size: 20px; font-variant-numeric: tabular-nums; }
.rec-kpi span { color: var(--text-muted); font-size: 12px; }
.rec-list { border-top: 1px solid var(--fx-line-soft); }
.record-row { display: flex; justify-content: space-between; padding: 9px 4px; border-bottom: 1px solid var(--fx-line-soft); color: var(--text-secondary); font-size: 12.5px; }
.record-row b { color: var(--primary-color); font-variant-numeric: tabular-nums; }
.rec-pager { display: flex; justify-content: flex-end; padding: 10px 2px 0; }

.settings-form { max-width: 480px; }
.hint { color: var(--text-placeholder); font-size: 11px; }

/* 分组管理弹窗 */
.group-manage-list { max-height: 220px; overflow: auto; }
.group-manage-row { display: flex; align-items: center; justify-content: space-between; padding: 6px 0; color: var(--text-secondary); font-size: 12.5px; }
.group-manage-acts .el-button + .el-button { margin-left: 4px; }
.group-create { display: flex; margin-top: 12px; gap: 8px; }
.empty { padding: 24px 0; color: var(--text-placeholder); font-size: 12px; text-align: center; }

@media (max-width: 1100px) {
  .aw-cards { grid-template-columns: 1fr; }
  .aw-layout { grid-template-columns: 1fr; }
  .shelf-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); }
}
</style>
