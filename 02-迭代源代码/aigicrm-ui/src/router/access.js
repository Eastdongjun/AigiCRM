export function firstAccessiblePath(routes, userStore) {
  const main = routes.find(route => route.path === '/')
  const children = main?.children || []
  const hit = children.find(route => {
    const permission = route.meta?.permission
    return permission && userStore.hasPermission(permission)
  })
  return hit ? `/${hit.path}` : '/403'
}
