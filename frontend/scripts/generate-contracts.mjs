import { readFile, writeFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDirectory = dirname(fileURLToPath(import.meta.url))
const frontendDirectory = resolve(scriptDirectory, '..')
const sourcePath = resolve(frontendDirectory, '../contracts/xiaoming-contract.json')
const outputPath = resolve(frontendDirectory, 'src/services/generated/contracts.ts')
const contract = JSON.parse(await readFile(sourcePath, 'utf8'))

const requiredKeys = [
  'schemaVersion',
  'apiBasePath',
  'product',
  'statuses',
  'intents',
  'errorCodes',
  'actionTypes',
  'cardTypes',
]

for (const key of requiredKeys) {
  if (!(key in contract)) {
    throw new Error(`Contract is missing required key: ${key}`)
  }
}
if (contract.product.productId !== '36743' || contract.intents.length !== 9) {
  throw new Error('Contract product or intent boundary does not match the frozen Phase 01 scope')
}

const literal = (value) => JSON.stringify(value, null, 2)
const generated = `// Generated from ../../../../contracts/xiaoming-contract.json. Do not edit by hand.\n\n` +
  `export const CONTRACT_SCHEMA_VERSION = ${literal(contract.schemaVersion)} as const\n` +
  `export const API_BASE_PATH = ${literal(contract.apiBasePath)} as const\n` +
  `export const PRODUCT = ${literal(contract.product)} as const\n\n` +
  `export const BUSINESS_STATUSES = ${literal(contract.statuses)} as const\n` +
  `export type BusinessStatus = (typeof BUSINESS_STATUSES)[number]\n\n` +
  `export const INTENTS = ${literal(contract.intents)} as const\n` +
  `export type XiaomingIntent = (typeof INTENTS)[number]['code']\n\n` +
  `export const ERROR_CODES = ${literal(contract.errorCodes)} as const\n` +
  `export type XiaomingErrorCode = (typeof ERROR_CODES)[number]['code']\n\n` +
  `export const ACTION_TYPES = ${literal(contract.actionTypes)} as const\n` +
  `export type ActionType = (typeof ACTION_TYPES)[number]\n\n` +
  `export const CARD_TYPES = ${literal(contract.cardTypes)} as const\n` +
  `export type CardType = (typeof CARD_TYPES)[number]\n`

if (process.argv.includes('--check')) {
  const current = await readFile(outputPath, 'utf8').catch(() => '')
  if (current !== generated) {
    console.error('Generated frontend contract is stale. Run npm run generate:contracts.')
    process.exitCode = 1
  }
} else {
  await writeFile(outputPath, generated)
  console.log(`Generated ${outputPath}`)
}
