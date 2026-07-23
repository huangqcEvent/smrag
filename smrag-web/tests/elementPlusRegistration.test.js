import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const mainSource = readFileSync(
  new URL('../src/main.js', import.meta.url),
  'utf8'
)

const requiredComponents = [
  'ElButton',
  'ElCheckbox',
  'ElCheckboxGroup',
  'ElDialog',
  'ElIcon',
  'ElInput',
  'ElOption',
  'ElSelect',
  'ElTable',
  'ElTableColumn',
  'ElTag',
  'ElTooltip',
  'ElUpload'
]

test('registers every Element Plus component used by application templates', () => {
  const registration = mainSource.match(
    /const elementComponents = \[([\s\S]*?)\]/
  )?.[1] || ''

  for (const component of requiredComponents) {
    assert.match(
      registration,
      new RegExp(`\\b${component}\\b`),
      `${component} must be included in elementComponents`
    )
  }
})

test('loads styles for overlay form components', () => {
  for (const style of [
    'el-checkbox.css',
    'el-checkbox-group.css',
    'el-dialog.css',
    'el-option.css',
    'el-select.css',
    'el-tag.css'
  ]) {
    assert.match(
      mainSource,
      new RegExp(`element-plus/theme-chalk/${style.replace('.', '\\.')}`),
      `${style} must be imported`
    )
  }
})
