import { describe, expect, it } from 'vitest'
import { shouldPromptForVersionChange } from './app-version'

describe('app version refresh policy', () => {
  it('does not interrupt local development when versions differ', () => {
    expect(shouldPromptForVersionChange('old', 'new', true)).toBe(false)
  })
  it('prompts production users when a deployed version changes', () => {
    expect(shouldPromptForVersionChange('old', 'new', false)).toBe(true)
  })
  it('does not prompt on first load or when versions match', () => {
    expect(shouldPromptForVersionChange('', 'new', false)).toBe(false)
    expect(shouldPromptForVersionChange('new', 'new', false)).toBe(false)
  })
})
