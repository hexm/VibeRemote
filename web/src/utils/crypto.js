/**
 * 前端加密工具 - 使用 @noble/ciphers (AES-256-GCM)
 * 不依赖 crypto.subtle，HTTP 环境下也可用
 * 密钥由服务器登录时下发，存储在 sessionStorage
 */
import { gcm } from '@noble/ciphers/aes.js'

const STORAGE_KEY = 'encKey'

function b64ToBytes(b64) {
  const bin = atob(b64)
  const bytes = new Uint8Array(bin.length)
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i)
  return bytes
}

function bytesToB64(bytes) {
  let bin = ''
  for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i])
  return btoa(bin)
}

/**
 * 加密文本，返回 "ivBase64:ciphertextBase64" 格式
 */
export async function encryptText(plaintext, keyBase64) {
  const key = b64ToBytes(keyBase64)
  const iv = crypto.getRandomValues(new Uint8Array(12))
  const encoded = new TextEncoder().encode(plaintext)
  const cipher = gcm(key, iv)
  const ciphertext = cipher.encrypt(encoded)
  return `${bytesToB64(iv)}:${bytesToB64(ciphertext)}`
}

/**
 * 解密 "ivBase64:ciphertextBase64" 格式的文本
 */
export async function decryptText(encrypted, keyBase64) {
  const [ivB64, ctB64] = encrypted.split(':', 2)
  const key = b64ToBytes(keyBase64)
  const iv = b64ToBytes(ivB64)
  const ciphertext = b64ToBytes(ctB64)
  const cipher = gcm(key, iv)
  const plaintext = cipher.decrypt(ciphertext)
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
