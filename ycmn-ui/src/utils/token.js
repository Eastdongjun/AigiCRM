// token 工具：本地解析 JWT，判断是否有效（存在且未过期）

export function getToken() {
  return localStorage.getItem('token')
}

export function clearToken() {
  localStorage.removeItem('token')
}

// 解析 JWT payload，失败返回 null
function decodeJwt(token) {
  try {
    const payload = token.split('.')[1]
    const json = decodeURIComponent(
      atob(payload.replace(/-/g, '+').replace(/_/g, '/'))
        .split('')
        .map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    )
    return JSON.parse(json)
  } catch {
    return null
  }
}

// token 是否有效：存在、可解析、且未过期。
// 无法解析或缺 exp 时，保守视为「无效」→ 走重新登录，避免带坏 token 打接口。
export function isTokenValid() {
  const token = getToken()
  if (!token) return false
  const payload = decodeJwt(token)
  if (!payload || !payload.exp) return false
  // exp 为秒级时间戳
  return payload.exp * 1000 > Date.now()
}
