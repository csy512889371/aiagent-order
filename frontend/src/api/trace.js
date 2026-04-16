const API_BASE = import.meta.env.VITE_API_BASE || "http://localhost:8080";

export async function fetchTrace(sessionId) {
  const response = await fetch(`${API_BASE}/api/chat/sessions/${sessionId}/trace`);
  const payload = await response.json();
  if (!response.ok || payload.code !== 0) {
    throw new Error(payload.message || "监控信息查询失败");
  }
  return payload;
}
