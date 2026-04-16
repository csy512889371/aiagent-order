const API_BASE = import.meta.env.VITE_API_BASE || "http://localhost:8080";

export async function createSession() {
  const response = await fetch(`${API_BASE}/api/chat/sessions`, {
    method: "POST"
  });
  return parseResponse(response);
}

export async function sendMessage(payload) {
  const response = await fetch(`${API_BASE}/api/chat/messages`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(payload)
  });
  return parseResponse(response);
}

export async function streamMessage(payload, handlers = {}) {
  const response = await fetch(`${API_BASE}/api/chat/messages/stream`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream"
    },
    body: JSON.stringify(payload)
  });

  if (!response.ok) {
    await throwResponseError(response);
  }

  const reader = response.body?.getReader();
  if (!reader) {
    throw new Error("当前浏览器不支持流式响应。");
  }

  const decoder = new TextDecoder("utf-8");
  let buffer = "";

  while (true) {
    const { done, value } = await reader.read();
    buffer += decoder.decode(value || new Uint8Array(), { stream: !done });

    let separatorIndex = buffer.indexOf("\n\n");
    while (separatorIndex >= 0) {
      const block = buffer.slice(0, separatorIndex).trim();
      buffer = buffer.slice(separatorIndex + 2);
      if (block) {
        dispatchSseBlock(block, handlers);
      }
      separatorIndex = buffer.indexOf("\n\n");
    }

    if (done) {
      break;
    }
  }

  const tail = buffer.trim();
  if (tail) {
    dispatchSseBlock(tail, handlers);
  }
}

export async function submitApproval(sessionId, payload) {
  const response = await fetch(`${API_BASE}/api/chat/sessions/${sessionId}/approval`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(payload)
  });
  return parseResponse(response);
}

async function parseResponse(response) {
  if (!response.ok) {
    await throwResponseError(response);
  }
  const payload = await response.json();
  if (payload.code !== 0) {
    const error = new Error(payload.message || "请求失败");
    error.code = payload.code;
    error.httpStatus = response.status;
    throw error;
  }
  return payload;
}

async function throwResponseError(response) {
  let payload = {};
  try {
    payload = await response.json();
  } catch (error) {
    payload = {};
  }
  const message = payload.message || "请求失败";
  const error = new Error(message);
  error.code = payload.code;
  error.httpStatus = response.status;
  throw error;
}

function dispatchSseBlock(block, handlers) {
  const lines = block.replace(/\r/g, "").split("\n");
  let eventName = "message";
  const dataLines = [];

  for (const line of lines) {
    if (line.startsWith("event:")) {
      eventName = line.slice(6).trim();
      continue;
    }
    if (line.startsWith("data:")) {
      dataLines.push(line.slice(5).trimStart());
    }
  }

  if (dataLines.length === 0) {
    return;
  }

  const rawData = dataLines.join("\n");
  let payload = rawData;
  try {
    payload = JSON.parse(rawData);
  } catch (error) {
    payload = rawData;
  }

  const handlerName = `on${eventName.charAt(0).toUpperCase()}${eventName.slice(1)}`;
  if (typeof handlers[handlerName] === "function") {
    handlers[handlerName](payload);
  }
}
