/**
 * 前端加密工具 - 使用 Web Crypto API (AES-256-GCM)
 * 密钥由服务器登录时下发，存储在 sessionStorage
 */

const STORAGE_KEY = 'encKey'

/** 将 Base64 字符串转为 Uint8Array */
function b64ToBytes(b64) {
  return Uint8Array.from(atob(b64), c => c.charCodeAt(0))
}

/** 将 Uint8Array 转为 Base64 字符串 */
function bytesToB64(bytes) {
  return btoa(String.fromCharCode(...bytes))
}

/** 从 Base64 密钥导入 CryptoKey 对象 */
async function importKey(keyBase64) {
  const keyBytes = b64ToBytes(keyBase64)
  return crypto.subtle.importKey('raw', keyBytes, { name: 'AES-GCM' }, false, ['encrypt', 'decrypt'])
}

/**
 * 加密文本
 * @param {string} plaintext
 * @param {string} keyBase64 - Base64 编码的 AES-256 密钥
 * @returns {Promise<string>} "ivBase64:ciphertextBase64" 格式
 */
export async function encryptText(plaintext, keyBase64) {
  const key = await importKey(keyBase64)
  const iv = crypto.getRandomValues(new Uint8Array(12))
  const encoded = new TextEncoder().encode(plaintext)
  const ciphertext = await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, encoded)
  return `${bytesToB64(iv)}:${bytesToB64(new Uint8Array(ciphertext))}`
}

/**
 * 解密文本
 * @param {string} encrypted - "ivBase64:ciphertextBase64" 格式
 * @param {string} keyBase64 - Base64 编码的 AES-256 密钥
 * @returns {Promise<string>} 明文
 */
export async function decryptText(encrypted, keyBase64) {
  const [ivB64, ctB64] = encrypted.split(':', 2)
  const key = await importKey(keyBase64)
  const iv = b64ToBytes(ivB64)
  const ciphertext = b64ToBytes(ctB64)
  const plaintext = await crypto.subtle.decrypt({ name: 'AES-GCM', iv }, key, ciphertext)
  return new TextDecoder().decode(plaintext)
}

/** 获取当前会话密钥（sessionStorage） */
export function getSessionKey() {
  return sessionStorage.getItem(STORAGE_KEY)
}

/** 存储会话密钥 */
export function setSessionKey(keyBase64) {
  sessionStorage.setItem(STORAGE_KEY, keyBase64)
}

/** 清除会话密钥（登出时调用） */
export function clearSessionKey() {
  sessionStorage.removeItem(STORAGE_KEY)
}
