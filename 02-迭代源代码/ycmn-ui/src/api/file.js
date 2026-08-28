import request from "@/utils/request";

export function getFolderTree(scope) {
  return request.get("/file/folders", { params: { scope } });
}

export function getFileList(params) {
  return request.get("/file/list", { params });
}

export function createFolder(data) {
  return request.post("/file/folders", data);
}

export function renameFolder(id, folderName) {
  return request.put(`/file/folders/${id}`, { folderName });
}

export function deleteFolder(id) {
  return request.delete(`/file/folders/${id}`);
}

export function deleteFile(id) {
  return request.delete(`/file/${id}`);
}

export function getStorageInfo() {
  return request.get("/file/storage");
}

export function shareFile(fileId, userIds) {
  return request.post(`/file/${fileId}/share`, { userIds });
}

export function getSharedToMe(params) {
  return request.get("/file/shared-to-me", { params });
}

export function getSharedToMeCount() {
  return request.get("/file/shared-to-me/count");
}

// === 文件权限体系（Task 5/6 接口）===

// 文件夹角色授权列表 [{roleId, roleName, perms[]}]
export function getFolderAcl(id) {
  return request.get(`/file/folders/${id}/acl`);
}

// 保存某角色的权限 {roleId, perms: []}
export function saveFolderAcl(id, data) {
  return request.post(`/file/folders/${id}/acl`, data);
}

// 移除某角色的授权
export function removeFolderAcl(id, roleId) {
  return request.delete(`/file/folders/${id}/acl/${roleId}`);
}

// 下载开关 {downloadable: 0|1}
export function setFolderDownloadable(id, downloadable) {
  return request.put(`/file/folders/${id}/download`, { downloadable });
}

// 当前用户对文件夹的权限视图 {perms: [], downloadable: 0|1}
export function getFolderPermissions(id) {
  return request.get(`/file/folders/${id}/permissions`);
}

// 【九项权限模型 v2 2026-08-28】ACL 弹窗角色下拉（登录即可，不占 system:role:view）
export function getAclRoles() {
  return request.get("/file/folders/roles");
}

// 文件覆盖替换（multipart "file"），返回更新后文件（含 version）
export function replaceFile(id, formData) {
  return request.post(`/file/${id}/replace`, formData, {
    headers: { "Content-Type": "multipart/form-data" },
  });
}
