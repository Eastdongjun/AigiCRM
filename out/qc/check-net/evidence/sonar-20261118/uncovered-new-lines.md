# P0-5 新代码未覆盖行清单（A2 扫描）
- 全仓新代码：lines_to_cover=1031，uncovered=449，new_coverage=56.5%
- 有未覆盖行的文件数：43
## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/approval/controller/ApprovalController.java
- to_cover=2 uncovered=2
| 行 | 代码 |
|---|---|
| 84 | `if (params != null && params.get(LIT_EXTENDDAYS) != null && !params.get(LIT_EXTENDDAYS).isBlank()) {` |
| 86 | `extendDays = Integer.valueOf(params.get(LIT_EXTENDDAYS).trim());` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：3 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/approval/service/ApprovalService.java
- to_cover=80 uncovered=14
| 行 | 代码 |
|---|---|
| 98 | `if (LIT_PENDING.equals(tab)) {` |
| 103 | `.eq(BizApprovalNode::getStatus, LIT_PENDING)` |
| 112 | `} else if (LIT_REVIEWING.equals(tab) || LIT_APPROVED.equals(tab) || LIT_REJECTED.equals(tab)) {` |
| 160 | `.eq(BizApprovalNode::getStatus, LIT_PENDING)` |
| 206 | `throw new BusinessException(LIT_X);` |
| 221 | `if (invalid && (LIT_PENDING.equals(node.getStatus()) || LIT_WAITING.equals(node.getStatus()))) {` |
| 226 | `if (LIT_PENDING.equals(node.getStatus()) || LIT_WAITING.equals(node.getStatus())) {` |
| 233 | `result.put(LIT_APPROVAL, approval);` |
| 237 | `.filter(n -> LIT_PENDING.equals(n.getStatus()))` |
| 250 | `List<Long> visible = scopeResolver.resolveModuleUsers(SecurityUtils.getCurrentUser(), "approval");` |
| 459 | `node.setStatus(LIT_PENDING);` |
| 471 | `node.setStatus(LIT_PENDING);` |
| 795 | `.eq(BizSeaPool::getStatus, LIT_PENDING_APPROVAL)` |
| 798 | `pool.setStatus(LIT_AVAILABLE);` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：20 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/auth/controller/AuthController.java
- to_cover=5 uncovered=5
| 行 | 代码 |
|---|---|
| 50 | `if (ua == null) return LIT_UNKNOWN;` |
| 51 | `String browser = LIT_UNKNOWN;` |
| 52 | `String os = LIT_UNKNOWN;` |
| 53 | `if (ua.contains(LIT_CHROME) && !ua.contains("Edg")) browser = LIT_CHROME;` |
| 56 | `else if (ua.contains("Safari") && !ua.contains(LIT_CHROME)) browser = "Safari";` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：4 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/customer/controller/CustomerController.java
- to_cover=6 uncovered=5
| 行 | 代码 |
|---|---|
| 352 | `if (!SecurityUtils.hasAuthority(LIT_FINANCE_VIEW)) {` |
| 508 | `if (!SecurityUtils.hasAuthority(LIT_FINANCE_VIEW)) return Result.ok(List.of());` |
| 516 | `if (!SecurityUtils.hasAuthority(LIT_FINANCE_VIEW)) return Result.ok(Map.of("totalCount", 0, "totalAmount", 0));` |
| 629 | `.excludeColumnFieldNames(List.of("deleted", LIT_CREATEDAT, "updatedAt", "id"))` |
| 647 | `result.put(LIT_CREATEDAT, info.getOrDefault(LIT_CREATEDAT, ""));` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：4 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/customer/service/CustomerService.java
- to_cover=114 uncovered=39
| 行 | 代码 |
|---|---|
| 326 | `allW.eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED); // B5 统一口径: 排sea+archived` |
| 333 | `.eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED) // 【B5 2026-09-22】归档不参与统计/提醒/到期检查(统一口径)` |
| 844 | `recycleBinService.record(LIT_CUSTOMER, id, snap, "用户删除", "user");` |
| 907 | `.in(com.aigicrm.module.approval.entity.BizApproval::getStatus, "reviewing", LIT_PENDING));` |
| 1057 | `applyDataScopeAndAutoShareFor(wrapper, scopeUser, getAutoShareOwnerIdsFor(scopeUser));` |
| 1058 | `}` |
| 1070 | `wrapper.eq(BizCustomer::getOwnerId, -1L);` |
| 1071 | `return;` |
| 1095 | `} else if (!extra.isEmpty()) {` |
| 1096 | `wrapper.in(BizCustomer::getOwnerId, extra);` |
| 1098 | `wrapper.eq(BizCustomer::getDeptId, -1L); // 空集合: 不可见` |
| 1162 | `w.eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED); // B5 统一口径: 排sea+archived` |
| 1178 | `w.eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED);` |
| 1179 | `applyDataScopeAndAutoShareFor(w, target);` |
| 1187 | `w.eq(BizCustomer::getDeleted, 0).eq(BizCustomer::getProtectionStatus, LIT_ACTIVE)` |
| 1188 | `.notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED);` |
| 1196 | `.notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED);` |
| 1227 | `w.eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED) // 【B5 2026-09-22】归档不参与统计/提醒/到期检查(统一口径)` |
| 1244 | `w.eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED) // 【B5 2026-09-22】归档不参与统计/提醒/到期检查(统一口径)` |
| 1267 | `w.eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED) // 【B5 2026-09-22】归档不参与统计/提醒/到期检查(统一口径)` |
| 1302 | `wrapper.eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED); // B5 统一口径: 排sea+archived` |
| 1310 | `wrapper.eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED); // B5 统一口径: 排sea+archived` |
| 1316 | `wrapper.eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED); // B5 统一口径: 排sea+archived` |
| 1401 | `String ruleWhere = PermissionRuleEngine.evaluateForEntity(user.getPermissionRules(), user, LIT_CUSTOMER);` |
| 1589 | `.ne(BizInvoice::getStatus, LIT_VOIDED)` |
| 1591 | `.last(LIT_LIMIT_1));` |
| 1620 | `throw new BusinessException(LIT_X_2);` |
| 1622 | `if (!LIT_PENDING.equals(invoice.getStatus())) {` |
| 1640 | `throw new BusinessException(LIT_X_2);` |
| 1657 | `throw new BusinessException(LIT_X_3);` |
| 1659 | `if (!LIT_PENDING.equals(payment.getStatus())) {` |
| 1677 | `throw new BusinessException(LIT_X_3);` |
| 1679 | `if (LIT_VOIDED.equals(payment.getStatus())) {` |
| 1682 | `payment.setStatus(LIT_VOIDED);` |
| 2226 | `r.setReminderType(LIT_SHARE_TO);` |
| 2228 | `r.setStatus(LIT_PENDING);` |
| 2237 | `r2.setStatus(LIT_PENDING);` |
| 2249 | `r3.setStatus(LIT_PENDING);` |
| 2374 | `.last(LIT_LIMIT_1));` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：49 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/customer/service/CustomerVisibilityService.java
- to_cover=3 uncovered=1
| 行 | 代码 |
|---|---|
| 57 | `String code = auth.actionCodeOf(LIT_CUSTOMER, "view");` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：3 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/fabushi/service/FabushiActivityService.java
- to_cover=8 uncovered=8
| 行 | 代码 |
|---|---|
| 47 | `m.put("id", a.getId()); m.put(LIT_TITLE, a.getTitle()); m.put(LIT_BOOKID, a.getBookId());` |
| 48 | `m.put("bookTitle", titles.get(a.getBookId())); m.put(LIT_DESCRIPTION, a.getDescription());` |
| 49 | `m.put(LIT_STARTDATE, a.getStartDate()); m.put(LIT_ENDDATE, a.getEndDate()); m.put("status", a.getStatus());` |
| 72 | `String title = body == null || body.get(LIT_TITLE) == null ? null : String.valueOf(body.get(LIT_TITLE)).trim();` |
| 73 | `Long bookId = body == null || body.get(LIT_BOOKID) == null ? null : Long.valueOf(String.valueOf(body.get(LIT_BOOKID)));` |
| 78 | `a.setDescription(body.get(LIT_DESCRIPTION) == null ? null : String.valueOf(body.get(LIT_DESCRIPTION)));` |
| 79 | `a.setStartDate(body.get(LIT_STARTDATE) == null ? LocalDate.now() : LocalDate.parse(String.valueOf(body.get(LIT_STARTDATE))));` |
| 80 | `a.setEndDate(body.get(LIT_ENDDATE) == null ? LocalDate.now().plusDays(30) : LocalDate.parse(String.valueOf(body.get(LIT_ENDDATE))));` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：7 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/fabushi/service/FabushiBookService.java
- to_cover=8 uncovered=7
| 行 | 代码 |
|---|---|
| 78 | `if (!LIT_APPROVED.equals(book.getStatus())` |
| 88 | `String st = (status == null || status.isBlank()) ? LIT_APPROVED : status;` |
| 90 | `if (!LIT_APPROVED.equals(st) && !canManage) st = LIT_APPROVED;` |
| 125 | `book.setStatus(LIT_APPROVED);` |
| 130 | `securityAuditService.permissionChange("fabushi_book_approve", LIT_BOOK + id + ":" + book.getTitle());` |
| 144 | `securityAuditService.permissionChange("fabushi_book_reject", LIT_BOOK + id + ":" + book.getTitle());` |
| 153 | `securityAuditService.permissionChange("fabushi_book_disable", LIT_BOOK + id + ":" + book.getTitle());` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：4 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/fabushi/service/FabushiDashboardService.java
- to_cover=67 uncovered=20
| 行 | 代码 |
|---|---|
| 68 | `boolean month = LIT_MONTH.equalsIgnoreCase(period);` |
| 75 | `data.put("period", month ? LIT_MONTH : "week");` |
| 77 | `data.put(LIT_MEMBERCOUNT, visible == null ? -1 : visible.size());` |
| 80 | `data.put(LIT_COVERAGEPCT, null);` |
| 84 | `data.put(LIT_RANKING, List.of());` |
| 85 | `data.put(LIT_DEPTPROGRESS, List.of());` |
| 87 | `data.put(LIT_TREND, List.of());` |
| 92 | `.eq(BizFabushiPost::getStatus, LIT_PUBLISHED)` |
| 103 | `.eq(BizFabushiComment::getStatus, LIT_PUBLISHED)` |
| 118 | `data.put(LIT_COVERAGEPCT, visible == null ? null` |
| 128 | `data.put(LIT_RANKING, buildRanking(visible, periodPosts, periodReads, startAt));` |
| 131 | `data.put(LIT_DEPTPROGRESS, buildDeptProgress(visible, periodPosts, periodLikes, periodComments, periodReads));` |
| 151 | `row.put(LIT_COUNT, e.getValue());` |
| 171 | `row.put(LIT_COUNT, e.getValue().size());` |
| 174 | `data.put(LIT_TREND, trend);` |
| 355 | `row.put(LIT_DEPTNAME, depts.getOrDefault(u.getDeptId(), "—"));` |
| 356 | `row.put(LIT_POSTCOUNT, p);` |
| 358 | `row.put(LIT_READDAYS, readDays.getOrDefault(uid, Set.of()).size());` |
| 450 | `c = Long.compare(((Number) b.get(LIT_READUSERS)).longValue(), ((Number) a.get(LIT_READUSERS)).longValue());` |
| 452 | `return Long.compare(((Number) b.get(LIT_MEMBERCOUNT)).longValue(), ((Number) a.get(LIT_MEMBERCOUNT)).longValue());` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：17 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/fabushi/service/FabushiPostService.java
- to_cover=26 uncovered=26
| 行 | 代码 |
|---|---|
| 68 | `boolean publishedOnly = !manageAllowed || status == null || status.isBlank() || LIT_PUBLISHED.equals(status);` |
| 70 | `.eq(BizFabushiPost::getStatus, publishedOnly ? LIT_PUBLISHED : status)` |
| 96 | `post.setContent(body.get(LIT_CONTENT) == null ? null : String.valueOf(body.get(LIT_CONTENT)));` |
| 110 | `Object content = body == null ? null : body.get(LIT_CONTENT);` |
| 119 | `if (post == null || !LIT_PUBLISHED.equals(post.getStatus())) {` |
| 120 | `throw new BusinessException(404, LIT_X);` |
| 136 | `if (post == null || !LIT_PUBLISHED.equals(post.getStatus())) throw new BusinessException(404, LIT_X);` |
| 140 | `Long fileId = body == null || body.get(LIT_FILEID) == null ? null : Long.valueOf(String.valueOf(body.get(LIT_FILEID)));` |
| 141 | `String fileType = body == null || body.get(LIT_FILETYPE) == null ? null : String.valueOf(body.get(LIT_FILETYPE));` |
| 153 | `if (body.get(LIT_DURATIONSECONDS) != null) row.setDurationSeconds(Integer.parseInt(String.valueOf(body.get(LIT_DURATIONSECONDS))));` |
| 155 | `return Map.of("id", row.getId(), LIT_FILEID, fileId, LIT_FILETYPE, fileType);` |
| 162 | `if (post == null) throw new BusinessException(404, LIT_X_2);` |
| 181 | `m.put("id", row.getId()); m.put("postId", row.getPostId()); m.put(LIT_FILEID, row.getFileId());` |
| 182 | `m.put(LIT_FILETYPE, row.getFileType()); m.put(LIT_DURATIONSECONDS, row.getDurationSeconds());` |
| 204 | `post.setStatus(LIT_PUBLISHED);` |
| 217 | `throw new BusinessException(404, LIT_X_2);` |
| 232 | `throw new BusinessException(404, LIT_X_2);` |
| 239 | `.set(BizFabushiPost::getStatus, LIT_PUBLISHED)` |
| 249 | `if (post == null || !LIT_PUBLISHED.equals(post.getStatus())) {` |
| 250 | `throw new BusinessException(404, LIT_X);` |
| 260 | `comment.setStatus(LIT_PUBLISHED);` |
| 269 | `.eq(BizFabushiComment::getStatus, LIT_PUBLISHED)` |
| 295 | `if (post == null || !LIT_PUBLISHED.equals(post.getStatus())) {` |
| 296 | `throw new BusinessException(404, LIT_X);` |
| 326 | `throw new BusinessException(404, LIT_X_2);` |
| 370 | `.eq(BizFabushiComment::getStatus, LIT_PUBLISHED).in(BizFabushiComment::getPostId, postIds)).stream()` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：9 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/fabushi/service/FabushiReadingService.java
- to_cover=17 uncovered=17
| 行 | 代码 |
|---|---|
| 67 | `m.put(LIT_LASTSECTIONID, row.getLastSectionId());` |
| 68 | `m.put(LIT_LASTPAGE, p == null ? 1 : p.getLastPage());` |
| 69 | `m.put(LIT_TOTALPAGES, p == null ? null : p.getTotalPages());` |
| 96 | `if (body.get(LIT_LASTSECTIONID) != null) row.setLastSectionId(Long.valueOf(String.valueOf(body.get(LIT_LASTSECTIONID))));` |
| 113 | `m.put(LIT_LASTPAGE, row == null || row.getLastPage() == null ? 1 : row.getLastPage());` |
| 114 | `m.put(LIT_TOTALPAGES, row == null ? null : row.getTotalPages());` |
| 121 | `int lastPage = body != null && body.get(LIT_LASTPAGE) != null ? Integer.parseInt(String.valueOf(body.get(LIT_LASTPAGE))) : 1;` |
| 123 | `Integer totalPages = body != null && body.get(LIT_TOTALPAGES) != null ? Integer.parseInt(String.valueOf(body.get(LIT_TOTALPAGES))) : null;` |
| 165 | `m.put(LIT_REMINDERENABLED, row == null ? 0 : row.getReminderEnabled());` |
| 166 | `m.put(LIT_FREQCODE, row == null ? LIT_DAILY : row.getFreqCode());` |
| 167 | `m.put(LIT_REMINDTIME, row == null ? "20:00" : row.getRemindTime());` |
| 168 | `m.put(LIT_SOUNDENABLED, row == null ? 1 : row.getSoundEnabled());` |
| 175 | `String freq = body != null && body.get(LIT_FREQCODE) != null ? String.valueOf(body.get(LIT_FREQCODE)) : LIT_DAILY;` |
| 176 | `if (!Set.of(LIT_DAILY, "weekday", "weekend").contains(freq)) throw new BusinessException(400, "提醒频率不合法");` |
| 177 | `String time = body != null && body.get(LIT_REMINDTIME) != null ? String.valueOf(body.get(LIT_REMINDTIME)) : "20:00";` |
| 179 | `int enabled = body != null && body.get(LIT_REMINDERENABLED) != null ? Integer.parseInt(String.valueOf(body.get(LIT_REMINDERENABLED))) : 0;` |
| 180 | `int sound = body != null && body.get(LIT_SOUNDENABLED) != null ? Integer.parseInt(String.valueOf(body.get(LIT_SOUNDENABLED))) : 1;` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：10 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/fabushi/service/FabushiSectionService.java
- to_cover=10 uncovered=10
| 行 | 代码 |
|---|---|
| 41 | `m.put("id", s.getId()); m.put("bookId", s.getBookId()); m.put(LIT_PARENTID, s.getParentId());` |
| 42 | `m.put(LIT_LEVELCODE, s.getLevelCode()); m.put(LIT_TITLE, s.getTitle()); m.put(LIT_SORTORDER, s.getSortOrder());` |
| 51 | `String title = body == null || body.get(LIT_TITLE) == null ? null : String.valueOf(body.get(LIT_TITLE)).trim();` |
| 55 | `row.setParentId(body.get(LIT_PARENTID) == null ? 0L : Long.valueOf(String.valueOf(body.get(LIT_PARENTID))));` |
| 56 | `row.setLevelCode(body.get(LIT_LEVELCODE) == null ? "section" : String.valueOf(body.get(LIT_LEVELCODE)));` |
| 58 | `row.setSortOrder(body.get(LIT_SORTORDER) == null ? 0 : Integer.parseInt(String.valueOf(body.get(LIT_SORTORDER))));` |
| 67 | `if (body != null && body.get(LIT_TITLE) != null) {` |
| 68 | `String title = String.valueOf(body.get(LIT_TITLE)).trim();` |
| 72 | `if (body != null && body.get(LIT_SORTORDER) != null) row.setSortOrder(Integer.parseInt(String.valueOf(body.get(LIT_SORTORDER))));` |
| 73 | `if (body != null && body.get(LIT_LEVELCODE) != null) row.setLevelCode(String.valueOf(body.get(LIT_LEVELCODE)));` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：6 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/fabushi/controller/FabushiStatsController.java
- to_cover=21 uncovered=21
| 行 | 代码 |
|---|---|
| 57 | `sb.append("覆盖率,").append(d.get(LIT_COVERAGEPCT) == null ? "" : d.get(LIT_COVERAGEPCT)).append("\n");` |
| 63 | `sb.append(r.get("userName")).append(',').append(r.get(LIT_DEPTNAME)).append(',')` |
| 65 | `.append(r.get(LIT_READDAYS)).append('\n');` |
| 89 | `sb.append(csv("月份")).append(',').append(csv(d.get(LIT_MONTH))).append('\n');` |
| 91 | `sb.append(csv("新增内容")).append(',').append(d.get(LIT_POSTS)).append('\n');` |
| 92 | `sb.append(csv("木鱼")).append(',').append(d.get(LIT_LIKES)).append('\n');` |
| 93 | `sb.append(csv("评论")).append(',').append(d.get(LIT_COMMENTS)).append('\n');` |
| 95 | `sb.append(csv("已读人数")).append(',').append(d.get(LIT_READUSERS)).append('\n');` |
| 97 | `sb.append(csv("覆盖率%")).append(',').append(csv(d.get(LIT_COVERAGEPCT))).append('\n');` |
| 99 | `sb.append('\n').append(csv("环比上月")).append(',').append(csv(prev.get(LIT_MONTH))).append('\n');` |
| 101 | `sb.append(csv("新增内容")).append(',').append(prev.get(LIT_POSTS)).append(',').append(csv(prev.get("postsPct"))).append('\n');` |
| 102 | `sb.append(csv("木鱼")).append(',').append(prev.get(LIT_LIKES)).append(',').append(csv(prev.get("likesPct"))).append('\n');` |
| 103 | `sb.append(csv("评论")).append(',').append(prev.get(LIT_COMMENTS)).append(',').append(csv(prev.get("commentsPct"))).append('\n');` |
| 104 | `sb.append(csv("已读人数")).append(',').append(prev.get(LIT_READUSERS)).append(',').append(csv(prev.get("readUsersPct"))).append('\n');` |
| 109 | `sb.append(csv(r.get(LIT_DEPTNAME))).append(',').append(r.get("memberCount")).append(',')` |
| 110 | `.append(r.get(LIT_READUSERS)).append(',').append(r.get(LIT_COVERAGEPCT)).append(',')` |
| 111 | `.append(r.get(LIT_POSTS)).append(',').append(r.get(LIT_LIKES)).append(',')` |
| 112 | `.append(r.get(LIT_COMMENTS)).append(',').append(r.get(LIT_READDAYS)).append('\n');` |
| 118 | `sb.append(csv(r.get("userName"))).append(',').append(csv(r.get(LIT_DEPTNAME))).append(',')` |
| 120 | `.append(r.get(LIT_READDAYS)).append('\n');` |
| 122 | `String filename = "fabushi-monthly-" + d.get(LIT_MONTH) + ".csv";` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：10 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/fabushi/service/FabushiStatsService.java
- to_cover=5 uncovered=3
| 行 | 代码 |
|---|---|
| 132 | `.eq(BizFabushiPost::getUserId, uid).eq(BizFabushiPost::getStatus, LIT_PUBLISHED)));` |
| 136 | `.eq(com.aigicrm.module.fabushi.entity.BizFabushiComment::getStatus, LIT_PUBLISHED)));` |
| 143 | `.eq(BizFabushiPost::getUserId, uid).eq(BizFabushiPost::getStatus, LIT_PUBLISHED)` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：3 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/file/service/FileChunkUploadService.java
- to_cover=3 uncovered=3
| 行 | 代码 |
|---|---|
| 109 | `Files.copy(chunk.getInputStream(), dir.resolve(index + LIT_PART), StandardCopyOption.REPLACE_EXISTING);` |
| 137 | `Path part = dir.resolve(i + LIT_PART);` |
| 211 | `return (int) stream.filter(p -> p.getFileName().toString().endsWith(LIT_PART)).count();` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：3 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FileController.java
- to_cover=2 uncovered=2
| 行 | 代码 |
|---|---|
| 175 | `fileService.renameFile(id, body == null ? null : body.get(LIT_FILENAME));` |
| 213 | `body.get(LIT_FILENAME) == null ? null : body.get(LIT_FILENAME).toString(),` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：3 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/file/service/FileGrantService.java
- to_cover=9 uncovered=5
| 行 | 代码 |
|---|---|
| 258 | `w.or(x -> x.eq(BizFileGrant::getSubjectType, LIT_POSITION)` |
| 277 | `if (LIT_ALLOW.equalsIgnoreCase(g.getEffect())) allow = true;` |
| 385 | `if (effect == null || effect.isBlank()) effect = LIT_ALLOW;` |
| 386 | `if (!Set.of(LIT_ALLOW, "DENY").contains(effect)) throw new BusinessException("效果仅支持 ALLOW/DENY");` |
| 539 | `w.or(x -> x.eq(BizFileGrant::getSubjectType, LIT_POSITION)` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：4 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/file/controller/FileRecycleController.java
- to_cover=12 uncovered=12
| 行 | 代码 |
|---|---|
| 75 | `return Result.ok(Map.of(LIT_MESSAGE, recycleService.restoreFile(id)));` |
| 82 | `return Result.ok(Map.of(LIT_MESSAGE, recycleService.hardDeleteFile(id)));` |
| 89 | `return Result.ok(Map.of(LIT_MESSAGE, recycleService.restoreFolder(id)));` |
| 96 | `return Result.ok(Map.of(LIT_MESSAGE, recycleService.hardDeleteFolder(id)));` |
| 112 | `results.put(LIT_FILE + id, "OK");` |
| 115 | `results.put(LIT_FILE + id, e.getMessage() == null ? "失败" : e.getMessage());` |
| 122 | `results.put(LIT_FOLDER + id, "OK");` |
| 125 | `results.put(LIT_FOLDER + id, e.getMessage() == null ? "失败" : e.getMessage());` |
| 148 | `results.put(LIT_FILE + id, "OK");` |
| 151 | `results.put(LIT_FILE + id, e.getMessage() == null ? "失败" : e.getMessage());` |
| 158 | `results.put(LIT_FOLDER + id, "OK");` |
| 161 | `results.put(LIT_FOLDER + id, e.getMessage() == null ? "失败" : e.getMessage());` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：5 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/file/service/FileScanService.java
- to_cover=3 uncovered=3
| 行 | 代码 |
|---|---|
| 50 | `} catch (InterruptedException e) {` |
| 51 | `Thread.currentThread().interrupt();` |
| 52 | `throw new BusinessException("病毒扫描被中断，文件已拒绝");` |

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/file/service/FileService.java
- to_cover=62 uncovered=21
| 行 | 代码 |
|---|---|
| 154 | `if (LIT_PUBLIC.equals(scope)) {` |
| 155 | `wrapper.eq(BizFileFolder::getFolderType, LIT_PUBLIC);` |
| 156 | `} else if (LIT_PRIVATE.equals(scope)) {` |
| 159 | `wrapper.eq(BizFileFolder::getFolderType, LIT_PRIVATE);` |
| 291 | `} else if (LIT_PRIVATE.equals(scope)) {` |
| 396 | `throw new BusinessException(403, LIT_X);` |
| 420 | `originalFilename = LIT_UNNAMED;` |
| 430 | `fileUrl = LIT_HTTPS + ossConfig.getBucketName() + "." + ossConfig.getEndpoint() + "/" + fileKey;` |
| 547 | `throw new BusinessException(403, LIT_X);` |
| 677 | `if (file == null) throw new BusinessException(LIT_X_2);` |
| 678 | `if (LIT_FABUSHI.equals(file.getFileScope())) {` |
| 683 | `throw new BusinessException(403, LIT_X);` |
| 758 | `: LIT_HTTPS + ossConfig.getBucketName() + "." + ossConfig.getEndpoint() + "/" + oldKey);` |
| 1082 | `throw new BusinessException(403, LIT_X);` |
| 1101 | `throw new BusinessException(403, LIT_X);` |
| 1270 | `if (file.getOssUrl() != null && file.getOssUrl().startsWith(LIT_HTTPS)) {` |
| 1308 | `if (e instanceof InterruptedException) {` |
| 1309 | `Thread.currentThread().interrupt();` |
| 1697 | `if (file != null && file.getOssUrl() != null && file.getOssUrl().startsWith(LIT_HTTPS)) {` |
| 2036 | `if (file == null) throw new BusinessException(LIT_X_2);` |
| 2042 | `throw new BusinessException(403, LIT_X);` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：14 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/file/service/FolderPermissionService.java
- to_cover=26 uncovered=4
| 行 | 代码 |
|---|---|
| 88 | `case P_VIEW, P_DOWNLOAD -> SecurityUtils.hasAuthority(LIT_FILE_VIEW);` |
| 90 | `case P_RENAME, P_REPLACE, P_EDIT -> SecurityUtils.hasAuthority(LIT_FILE_EDIT);` |
| 91 | `case P_SHARE -> SecurityUtils.hasAuthority(LIT_FILE_SHARE);` |
| 122 | `if (SecurityUtils.hasAuthority(LIT_FABUSHI_VIEW)) { p.add(P_VIEW); p.add(P_DOWNLOAD); }` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：9 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/common/controller/FormDraftController.java
- to_cover=2 uncovered=2
| 行 | 代码 |
|---|---|
| 42 | `String data = body == null || body.get(LIT_DRAFTDATA) == null ? null : body.get(LIT_DRAFTDATA).toString();` |
| 59 | `String data = body == null || body.get(LIT_DRAFTDATA) == null ? null : body.get(LIT_DRAFTDATA).toString();` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：3 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/incentive/service/IncentiveService.java
- to_cover=76 uncovered=13
| 行 | 代码 |
|---|---|
| 139 | `m.put(LIT_REALNAME, u.getRealName());` |
| 142 | `list.sort(Comparator.comparing(m -> (String) m.getOrDefault(LIT_REALNAME, "")));` |
| 635 | `case LIT_FILEMIDID -> existing.setFileMidId(v.trim());` |
| 636 | `case LIT_FILEPOSTID -> existing.setFilePostId(v.trim());` |
| 637 | `case LIT_FILEID -> existing.setFileId(v.trim());` |
| 638 | `case LIT_FILETRACKID -> existing.setFileTrackId(v.trim());` |
| 671 | `case LIT_TRACK -> existing.getFileTrackIds();` |
| 696 | `case LIT_TRACK -> existing.setFileTrackIds(next);` |
| 713 | `case LIT_TRACK -> uw.set(BizIncentiveRecord::getFileTrackId, nextJson);` |
| 916 | `return parent != null && (LIT_VISIT.equals(parent.getCategoryCode()) || LIT_EXCHANGE.equals(parent.getCategoryCode()));` |
| 938 | `case LIT_EXCHANGE -> 2;` |
| 939 | `case LIT_VISIT -> 3;` |
| 1516 | `item.put(LIT_TOTALSCORE, totalScore);` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：35 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/system/service/LifecycleService.java
- to_cover=38 uncovered=27
| 行 | 代码 |
|---|---|
| 166 | `Map<String, Object> result = LIT_HANDOVER.equals(type)` |
| 173 | `out.put(LIT_RESULT, result);` |
| 181 | `if (!SecurityUtils.isAdmin() && !current.equals(longVal(req.get(LIT_APPROVER_ID)))) {` |
| 196 | `if (req == null) throw new BusinessException(LIT_X);` |
| 197 | `if (!"approved".equals(req.get(LIT_STATUS))) throw new BusinessException("只有已通过的请求可以回滚");` |
| 200 | `Map<String, Integer> counts = LIT_HANDOVER.equals(type)` |
| 206 | `out.put(LIT_RESULT, counts);` |
| 214 | `long from = longVal(req.get(LIT_SUBJECT_USER_ID));` |
| 218 | `Long toDept = longVal(target.get(LIT_DEPT_ID));` |
| 246 | `counts.put(LIT_CUSTOMER, move(LIT_CUSTOMER, "biz_customer", LIT_OWNER_ID, id, from, to));` |
| 248 | `counts.put(LIT_REMINDER, moveNoDeleted(LIT_REMINDER, "biz_reminder", LIT_OWNER_ID, id, from, to));` |
| 250 | `counts.put(LIT_FOLDER, move(LIT_FOLDER, "biz_file_folder", LIT_OWNER_ID, id, from, to));` |
| 307 | `return jdbc.update("UPDATE " + table + " SET " + col + "=? WHERE " + col + LIT_AND_DELETED_0,` |
| 316 | `return jdbc.update("UPDATE " + table + " SET " + col + "=? WHERE " + col + LIT_AND_DELETED_0, to, from);` |
| 323 | `long userId = longVal(req.get(LIT_SUBJECT_USER_ID));` |
| 327 | `Long oldDept = longVal(user.get(LIT_DEPT_ID));` |
| 347 | `counts.put(LIT_CUSTOMER, jdbc.update("UPDATE biz_customer SET dept_id=?, office_id=?, updated_at=NOW() WHERE owner_id=? AND deleted=0",` |
| 358 | `counts.put(LIT_FOLDER, jdbc.update("UPDATE biz_file_folder SET org_id=? WHERE owner_id=? AND deleted=0", toOrg, userId));` |
| 364 | `counts.put(LIT_REMINDER, jdbc.update("UPDATE biz_reminder SET office_id=? WHERE owner_id=? AND deleted=0", toOffice, userId));` |
| 385 | `counts.put(LIT_CUSTOMER, jdbc.update("UPDATE biz_customer c JOIN biz_lifecycle_item i ON i.biz_id=c.id " +` |
| 389 | `counts.put(LIT_REMINDER, jdbc.update("UPDATE biz_reminder r JOIN biz_lifecycle_item i ON i.biz_id=r.id " +` |
| 393 | `counts.put(LIT_FOLDER, jdbc.update("UPDATE biz_file_folder f JOIN biz_lifecycle_item i ON i.biz_id=f.id " +` |
| 415 | `long userId = longVal(req.get(LIT_SUBJECT_USER_ID));` |
| 422 | `counts.put(LIT_CUSTOMER, jdbc.update("UPDATE biz_customer c JOIN biz_lifecycle_item i ON i.biz_id=c.id " +` |
| 427 | `counts.put(LIT_FOLDER, jdbc.update("UPDATE biz_file_folder f JOIN biz_lifecycle_item i ON i.biz_id=f.id " +` |
| 429 | `counts.put(LIT_REMINDER, jdbc.update("UPDATE biz_reminder r JOIN biz_lifecycle_item i ON i.biz_id=r.id " +` |
| 595 | `"request=" + id + " old=" + req.get(LIT_APPROVER_ID) + " new=" + newApproverId` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：16 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/system/service/OrgAdminService.java
- to_cover=3 uncovered=2
| 行 | 代码 |
|---|---|
| 126 | `if (SecurityUtils.hasAuthority(LIT_SYSTEM_ORG_ADMIN)) return true;` |
| 133 | `if (SecurityUtils.hasAuthority(LIT_SYSTEM_ORG_ADMIN)) return java.util.Collections.emptyList();` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：3 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/system/service/PermCenterService.java
- to_cover=2 uncovered=2
| 行 | 代码 |
|---|---|
| 112 | `if (LIT_CUSTOMER.equals(module) && "view".equals(action)) {` |
| 194 | `.evaluateForEntity(rules == null ? List.of() : rules, target, LIT_CUSTOMER));` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：7 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/system/service/PermissionMetadataService.java
- to_cover=19 uncovered=2
| 行 | 代码 |
|---|---|
| 109 | `meta.put("scopeAxis", Map.of(LIT_MODULE, scopeModule, "action",` |
| 110 | `scopeAction == null ? "view" : scopeAction));` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：10 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/system/service/PermReconcileService.java
- to_cover=3 uncovered=2
| 行 | 代码 |
|---|---|
| 84 | `boolean same = Boolean.TRUE.equals(row.get(LIT_MATCH));` |
| 96 | `out.put(LIT_MATCH, diff == 0);` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：3 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/RecycleController.java
- to_cover=15 uncovered=13
| 行 | 代码 |
|---|---|
| 59 | `return Result.ok(Map.of(LIT_MESSAGE, recycleService.restoreCustomer(id)));` |
| 66 | `return Result.ok(Map.of(LIT_MESSAGE, recycleService.hardDeleteCustomer(id)));` |
| 88 | `return Result.ok(Map.of(LIT_MESSAGE, recycleService.restoreUser(id)));` |
| 95 | `return Result.ok(Map.of(LIT_MESSAGE, recycleService.hardDeleteUser(id)));` |
| 113 | `return Result.ok(Map.of(LIT_MESSAGE, recycleService.restoreTask(id)));` |
| 120 | `return Result.ok(Map.of(LIT_MESSAGE, recycleService.hardDeleteTask(id)));` |
| 136 | `return Result.ok(Map.of(LIT_MESSAGE, recycleService.restoreReminder(id)));` |
| 143 | `return Result.ok(Map.of(LIT_MESSAGE, recycleService.hardDeleteReminder(id)));` |
| 159 | `return Result.ok(Map.of(LIT_MESSAGE, recycleService.restoreIncentive(id)));` |
| 166 | `return Result.ok(Map.of(LIT_MESSAGE, recycleService.hardDeleteIncentive(id)));` |
| 202 | `return Result.ok(Map.of(LIT_MESSAGE, recycleService.hardDeleteFile(id)));` |
| 221 | `return Result.ok(Map.of(LIT_MESSAGE, recycleService.restoreFolder(id)));` |
| 228 | `return Result.ok(Map.of(LIT_MESSAGE, recycleService.hardDeleteFolder(id)));` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：3 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/system/service/RecycleExportService.java
- to_cover=32 uncovered=1
| 行 | 代码 |
|---|---|
| 184 | `Object fp = r.get(LIT_FILE_PATH);` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：13 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/system/service/RecycleService.java
- to_cover=33 uncovered=23
| 行 | 代码 |
|---|---|
| 141 | `throw new BusinessException(LIT_X_7);` |
| 171 | `throw new BusinessException(LIT_X_6);` |
| 180 | `throw new BusinessException(LIT_X_7);` |
| 196 | `throw new BusinessException(LIT_X_5);` |
| 206 | `throw new BusinessException(LIT_X_6);` |
| 243 | `throw new BusinessException(LIT_X_5);` |
| 261 | `throw new BusinessException(LIT_X_3);` |
| 274 | `throw new BusinessException(LIT_X_6);` |
| 293 | `throw new BusinessException(LIT_X_3);` |
| 308 | `throw new BusinessException(LIT_X_4);` |
| 401 | `throw new BusinessException(LIT_X_7);` |
| 540 | `throw new BusinessException(LIT_X_3);` |
| 633 | `throw new BusinessException(LIT_X_4);` |
| 907 | `if (n != 1) throw new BusinessException(LIT_X);` |
| 916 | `if (n != 1) throw new BusinessException(LIT_X_2);` |
| 951 | `Long userId = r.get(LIT_USER_ID) == null ? null : ((Number) r.get(LIT_USER_ID)).longValue();` |
| 955 | `Long customerId = r.get(LIT_CUSTOMER_ID) == null ? null : ((Number) r.get(LIT_CUSTOMER_ID)).longValue();` |
| 963 | `if (n != 1) throw new BusinessException(LIT_X);` |
| 982 | `long uid = u.path(LIT_USER_ID).asLong(0);` |
| 996 | `if (n != 1) throw new BusinessException(LIT_X_2);` |
| 1032 | `Long customerId = r.get(LIT_CUSTOMER_ID) == null ? null : ((Number) r.get(LIT_CUSTOMER_ID)).longValue();` |
| 1040 | `if (n != 1) throw new BusinessException(LIT_X);` |
| 1049 | `if (n != 1) throw new BusinessException(LIT_X_2);` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：13 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/reminder/service/ReminderService.java
- to_cover=36 uncovered=13
| 行 | 代码 |
|---|---|
| 407 | `String val = configService.getValue(LIT_CUSTOMER_WARNING_DAYS);` |
| 418 | `.notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED); // B5 统一口径: 排sea+archived` |
| 419 | `List<Long> visibleOwners = scopeResolver.resolveModuleUsers(SecurityUtils.getCurrentUser(), LIT_REMINDER);` |
| 431 | `.eq(BizReminder::getReminderType, LIT_EXPIRING)` |
| 432 | `.eq(BizReminder::getStatus, LIT_PENDING));` |
| 436 | `.eq(BizReminder::getReminderType, LIT_EXPIRING)` |
| 437 | `.eq(BizReminder::getStatus, LIT_PROCESSED)` |
| 442 | `r.setReminderType(LIT_EXPIRING);` |
| 446 | `r.setStatus(LIT_PENDING);` |
| 527 | `snap.put(LIT_CUSTOMERID, reminder.getCustomerId());` |
| 528 | `snap.put(LIT_OWNERID, reminder.getOwnerId());` |
| 530 | `snap.put(LIT_STATUS, reminder.getStatus());` |
| 531 | `recycleBinService.record(LIT_REMINDER, id, snap, reminder.getDeletedReason(), "user");` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：11 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/system/service/RolePermScopeService.java
- to_cover=26 uncovered=18
| 行 | 代码 |
|---|---|
| 130 | `throw new BusinessException(503, LIT_X);` |
| 142 | `c.setModule(rs.getString(LIT_MODULE));` |
| 143 | `c.setAction(rs.getString(LIT_ACTION));` |
| 144 | `c.setScopeLevel(rs.getString(LIT_SCOPE_LEVEL));` |
| 145 | `c.setAnchor(rs.getString(LIT_ANCHOR));` |
| 208 | `throw new BusinessException(503, LIT_X);` |
| 339 | `recordScopeVersion(assigneeType, assigneeKey, String.valueOf(row.get(LIT_MODULE)), String.valueOf(row.get(LIT_ACTION)),` |
| 340 | `row.get(LIT_SCOPE_LEVEL) + "/" + row.get(LIT_ANCHOR), "默认(未配置)");` |
| 378 | `&& (LIT_REGION.equals(scope) || LIT_OFFICE.equals(scope))) {` |
| 379 | `int wantType = LIT_REGION.equals(scope) ? 2 : 3;` |
| 401 | `oldScope = row.get(LIT_SCOPE_LEVEL) + "/" + row.get(LIT_ANCHOR);` |
| 430 | `case LIT_CUSTOM: return "CUSTOM";` |
| 431 | `case LIT_REGION: return "MY_REGION_SUB";` |
| 432 | `case LIT_OFFICE: return "MY_OFFICE_SUB";` |
| 521 | `String anchor = LIT_OWNER;` |
| 525 | `scopeLevel = String.valueOf(row.get(LIT_SCOPE_LEVEL));` |
| 526 | `anchor = String.valueOf(row.get(LIT_ANCHOR));` |
| 649 | `throw new BusinessException(503, LIT_X);` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：12 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/score/service/ScoreService.java
- to_cover=26 uncovered=26
| 行 | 代码 |
|---|---|
| 180 | `Long userId = toLong(e.get(LIT_USERID));` |
| 274 | `m.put(LIT_REALNAME, u.getRealName());` |
| 349 | `row.put(LIT_USERID, uid);` |
| 350 | `row.put(LIT_REALNAME, user.getRealName());` |
| 351 | `row.put(LIT_OFFICENAME, officeName);` |
| 352 | `row.put(LIT_TOTALSCORE, entry.getValue());` |
| 353 | `row.put(LIT_ITEMCOUNT, itemCounts.getOrDefault(uid, 0));` |
| 369 | `if (!deptName.equals(p.getOrDefault(LIT_OFFICENAME, "未知"))) continue;` |
| 371 | `m.put(LIT_USERID, p.get(LIT_USERID));` |
| 372 | `m.put(LIT_REALNAME, p.get(LIT_REALNAME));` |
| 373 | `m.put(LIT_TOTALSCORE, p.get(LIT_TOTALSCORE));` |
| 374 | `m.put(LIT_ITEMCOUNT, p.get(LIT_ITEMCOUNT));` |
| 377 | `total += (int) p.getOrDefault(LIT_TOTALSCORE, 0);` |
| 379 | `members.sort((a, b) -> (int) b.get(LIT_TOTALSCORE) - (int) a.get(LIT_TOTALSCORE));` |
| 383 | `result.put(LIT_PARTICIPANTCOUNT, members.size());` |
| 384 | `result.put(LIT_TOTALSCORE, total);` |
| 395 | `String dept = (String) p.getOrDefault(LIT_OFFICENAME, "未知");` |
| 397 | `agg[0] += (int) p.getOrDefault(LIT_TOTALSCORE, 0);` |
| 411 | `row.put(LIT_TOTALSCORE, e.getValue()[0]);` |
| 412 | `row.put(LIT_PARTICIPANTCOUNT, e.getValue()[1]);` |
| 422 | `stats.put(LIT_PARTICIPANTCOUNT, personal.size());` |
| 429 | `depts.add((String) p.getOrDefault(LIT_OFFICENAME, ""));` |
| 430 | `int score = (int) p.getOrDefault(LIT_TOTALSCORE, 0);` |
| 433 | `topName = (String) p.getOrDefault(LIT_REALNAME, "");` |
| 435 | `totalItems += (int) p.getOrDefault(LIT_ITEMCOUNT, 0);` |
| 444 | `stats.put("topItemCount", personal.get(0).getOrDefault(LIT_ITEMCOUNT, 0));` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：8 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/sea/service/SeaConfigService.java
- to_cover=9 uncovered=9
| 行 | 代码 |
|---|---|
| 85 | `try { securityAudit.permissionChange("sea_pool_create", LIT_SEAPOOL + config.getPoolName()); }` |
| 100 | `try { securityAudit.permissionChange("sea_pool_update", LIT_SEAPOOL + config.getId()); }` |
| 139 | `try { securityAudit.permissionChange("sea_pool_delete", LIT_SEAPOOL + id); }` |
| 315 | `group.put(LIT_LABEL, "未分配部门");` |
| 316 | `group.put(LIT_DISABLED, true);` |
| 329 | `node.put(LIT_LABEL, d.getDeptName());` |
| 331 | `node.put(LIT_DISABLED, !selectable);` |
| 347 | `n.put(LIT_LABEL, u.getRealName());` |
| 348 | `n.put(LIT_DISABLED, false);` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：5 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/sea/controller/SeaController.java
- to_cover=5 uncovered=5
| 行 | 代码 |
|---|---|
| 154 | `wrapper.eq(BizApproval::getApprovalType, LIT_CLAIM)` |
| 233 | `.eq(BizApproval::getApprovalType, LIT_CLAIM);` |
| 238 | `.eq(BizApproval::getApprovalType, LIT_CLAIM).eq(BizApproval::getStatus, "approved")` |
| 241 | `.eq(BizApproval::getApprovalType, LIT_CLAIM).eq(BizApproval::getStatus, "rejected")` |
| 244 | `.eq(BizApproval::getApprovalType, LIT_CLAIM).eq(BizApproval::getStatus, "reviewing")` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：3 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/sea/service/SeaService.java
- to_cover=13 uncovered=7
| 行 | 代码 |
|---|---|
| 81 | `wrapper.ne(BizSeaPool::getStatus, LIT_CLAIMED);` |
| 152 | `stats.put("total", seaPoolMapper.selectCount(scoped(visiblePoolIds).eq(BizSeaPool::getStatus, LIT_AVAILABLE)));` |
| 156 | `.eq(BizSeaPool::getStatus, LIT_CLAIMED).ge(BizSeaPool::getClaimDate, monthStart)));` |
| 177 | `.ne(BizSeaPool::getStatus, LIT_CLAIMED));` |
| 201 | `pool.setStatus(LIT_AVAILABLE);` |
| 324 | `throw new BusinessException(403, LIT_X);` |
| 339 | `throw new BusinessException(LIT_X);` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：5 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/security/SecurityAuditService.java
- to_cover=14 uncovered=4
| 行 | 代码 |
|---|---|
| 110 | `event(LIT_PERMISSION_CHANGE, LIT_ACTION, action, "target", targetDesc, LIT_ACTORID, actorId);` |
| 111 | `persist(LIT_PERMISSION_CHANGE, actorId, null, null, LIT_ACTION_2 + action + " " + targetDesc);` |
| 138 | `event("USER_ORG_CHANGED", LIT_USERID, userId, "from", oldOrgId, "to", newOrgId, LIT_ACTORID, actorId);` |
| 156 | `"canEdit", canEdit, "canDelete", canDelete, LIT_ACTORID, actorId);` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：12 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/statistics/service/StatisticsQueryService.java
- to_cover=81 uncovered=31
| 行 | 代码 |
|---|---|
| 366 | `item.put(LIT_CUSTOMERNAME, c.getCustomerName());` |
| 499 | `else if (LIT_QUASI.equals(ct)) quasiCount++;` |
| 547 | `return String.valueOf(a.getOrDefault(LIT_CUSTOMERNAME, "")).compareTo(String.valueOf(b.getOrDefault(LIT_CUSTOMERNAME, "")));` |
| 600 | `row.put(LIT_DEPTNAME, deptName);` |
| 601 | `row.put(LIT_CUSTOMERCOUNT, custCount);` |
| 602 | `row.put(LIT_CORECOUNT, cores);` |
| 603 | `row.put(LIT_AVGSCORE, avgScoreP);` |
| 605 | `row.put(LIT_TOTALSCORE, totalScore);` |
| 610 | `int cmp = Integer.compare(((Number) b.get(LIT_TOTALSCORE)).intValue(), ((Number) a.get(LIT_TOTALSCORE)).intValue());` |
| 612 | `return Long.compare(((Number) b.get(LIT_CUSTOMERCOUNT)).longValue(), ((Number) a.get(LIT_CUSTOMERCOUNT)).longValue());` |
| 618 | `String dn = (String) p.getOrDefault(LIT_DEPTNAME, "未知");` |
| 620 | `agg[0] += ((Number) p.get(LIT_TOTALSCORE)).intValue();` |
| 621 | `agg[1] += ((Number) p.get(LIT_CUSTOMERCOUNT)).intValue();` |
| 622 | `agg[2] += ((Number) p.get(LIT_CORECOUNT)).intValue();` |
| 628 | `row.put(LIT_DEPTNAME, e.getKey());` |
| 629 | `row.put(LIT_TOTALSCORE, e.getValue()[0]);` |
| 630 | `row.put(LIT_CUSTOMERCOUNT, e.getValue()[1]);` |
| 631 | `row.put(LIT_CORECOUNT, e.getValue()[2]);` |
| 633 | `row.put(LIT_AVGSCORE, e.getValue()[1] > 0 ? Math.round(e.getValue()[0] * 10.0 / e.getValue()[1]) / 10.0 : 0);` |
| 745 | `int cmp = Integer.compare(((Number) b.getOrDefault(LIT_RELATIONSHIPSCORE, 0)).intValue(),` |
| 746 | `((Number) a.getOrDefault(LIT_RELATIONSHIPSCORE, 0)).intValue());` |
| 748 | `return String.valueOf(a.getOrDefault(LIT_CUSTOMERNAME, "")).compareTo(String.valueOf(b.getOrDefault(LIT_CUSTOMERNAME, "")));` |
| 821 | `int cmp = Integer.compare(((Number) b.getOrDefault(LIT_TOTALSCORE, 0)).intValue(),` |
| 822 | `((Number) a.getOrDefault(LIT_TOTALSCORE, 0)).intValue());` |
| 824 | `return String.valueOf(a.getOrDefault(LIT_CUSTOMERNAME, "")).compareTo(String.valueOf(b.getOrDefault(LIT_CUSTOMERNAME, "")));` |
| 875 | `.notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED)` |
| 888 | `w.eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED) // 【B5 2026-09-22】归档不参与统计/提醒/到期检查(统一口径)` |
| 898 | `.notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED)` |
| 904 | `nw.eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED) // 【B5 2026-09-22】归档不参与统计/提醒/到期检查(统一口径)` |
| 916 | `rw.eq(BizCustomer::getDeleted, 0).notIn(BizCustomer::getStatus, "sea", LIT_ARCHIVED) // 【B5 2026-09-22】归档不参与统计/提醒/到期检查(统一口径)` |
| 925 | `row.put(LIT_CUSTOMERCOUNT, total);` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：21 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/system/service/SysDictService.java
- to_cover=2 uncovered=2
| 行 | 代码 |
|---|---|
| 173 | `.eq(LIT_INDUSTRY.equals(column)` |
| 176 | `.set(LIT_INDUSTRY.equals(column)` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：4 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/system/controller/SysUserController.java
- to_cover=3 uncovered=1
| 行 | 代码 |
|---|---|
| 80 | `if (!orgAdminService.canManageUser(id)) throw new BusinessException(403, LIT_X);` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：3 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/system/service/SysUserService.java
- to_cover=18 uncovered=4
| 行 | 代码 |
|---|---|
| 567 | `throw new BusinessException(403, LIT_X);` |
| 584 | `if (user == null) throw new BusinessException(LIT_X_2);` |
| 604 | `if (user == null) throw new BusinessException(LIT_X_2);` |
| 656 | `throw new BusinessException(LIT_X_2);` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：7 行

## 02-迭代源代码/aigicrm-backend/src/main/java/com/aigicrm/module/task/service/TaskService.java
- to_cover=25 uncovered=14
| 行 | 代码 |
|---|---|
| 57 | `if (body.get(LIT_TITLE) != null) task.setTitle(body.get(LIT_TITLE).toString());` |
| 58 | `if (body.get(LIT_DETAIL) != null) task.setDetail(body.get(LIT_DETAIL).toString());` |
| 60 | `if (body.get(LIT_DUEDATE) != null) task.setDueDate(LocalDate.parse(body.get(LIT_DUEDATE).toString()));` |
| 69 | `if (body.get(LIT_TITLE) != null) task.setTitle(body.get(LIT_TITLE).toString());` |
| 70 | `if (body.get(LIT_DETAIL) != null) task.setDetail(body.get(LIT_DETAIL).toString());` |
| 71 | `if (body.get(LIT_DUEDATE) != null) task.setDueDate(LocalDate.parse(body.get(LIT_DUEDATE).toString()));` |
| 143 | `if (LIT_COMPLETED.equals(task.getStatus())) {` |
| 147 | `if (LIT_CANCELLED.equals(task.getStatus())) {` |
| 150 | `task.setStatus(LIT_CANCELLED);` |
| 159 | `if (LIT_COMPLETED.equals(task.getStatus())) {` |
| 170 | `snap.put(LIT_TITLE, task.getTitle());` |
| 202 | `if (LIT_COMPLETED.equals(existing.getStatus())) {` |
| 251 | `wrapper.eq(BizTask::getStatus, LIT_PENDING)` |
| 309 | `task.setOverdue(LIT_PENDING.equals(task.getStatus())` |
- 新非指令行（注释/常量声明等，不入覆盖率分母）：8 行

