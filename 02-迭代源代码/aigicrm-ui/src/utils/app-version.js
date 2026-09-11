export function shouldPromptForVersionChange(oldVersion, currentVersion, isDevelopment) {
  return Boolean(oldVersion && currentVersion && oldVersion !== currentVersion && !isDevelopment)
}
