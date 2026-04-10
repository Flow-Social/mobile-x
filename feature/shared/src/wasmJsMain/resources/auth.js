(function () {
  window.flowAuthSignInWithGoogle = function (clientId, onSuccess, onError) {
    try {
      if (!window.google || !window.google.accounts || !window.google.accounts.id) {
        onError("Google Identity Services is not loaded");
        return;
      }

      google.accounts.id.initialize({
        client_id: clientId,
        callback: function (response) {
          const credential = response && response.credential;
          if (!credential) {
            onError("Google credential is missing");
            return;
          }
          onSuccess(credential);
        }
      });

      google.accounts.id.prompt(function (notification) {
        try {
          if (notification && typeof notification.isNotDisplayed === "function" && notification.isNotDisplayed()) {
            const reason = typeof notification.getNotDisplayedReason === "function"
              ? notification.getNotDisplayedReason()
              : "unknown";
            console.warn("Google One Tap not displayed:", reason);
          }

          if (notification && typeof notification.isSkippedMoment === "function" && notification.isSkippedMoment()) {
            const reason = typeof notification.getSkippedReason === "function"
              ? notification.getSkippedReason()
              : "unknown";
            console.warn("Google One Tap skipped:", reason);
          }
        } catch (notificationError) {
          console.warn("Google One Tap notification error:", notificationError);
        }
      });
    } catch (errorValue) {
      onError(String(errorValue));
    }
  };

  window.flowAuthExchangeIdToken = async function (idToken, apiUrl, onSuccess, onError) {
    try {
      const backendResponse = await fetch(`${apiUrl}/auth/google`, {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded"
        },
        body: new URLSearchParams({
          type: "mobile",
          id_token: idToken
        }).toString()
      });

      const backendText = await backendResponse.text();

      if (!backendResponse.ok) {
        onError(`Backend auth failed: ${backendResponse.status}`);
        return;
      }

      onSuccess(backendText);
    } catch (errorValue) {
      onError(String(errorValue));
    }
  };

  window.flowAuthWriteLocalStorage = function (key, value) {
    localStorage.setItem(key, value);
  };

  window.flowAuthReadLocalStorage = function (key) {
    return localStorage.getItem(key);
  };

  window.flowProfileApiGet = async function (path, apiUrl, authToken, onSuccess, onError) {
    try {
      const response = await fetch(`${apiUrl}${path}`, {
        method: "GET",
        headers: {
          "Authorization": `Bearer ${authToken}`,
          "Accept": "application/json"
        }
      });

      const body = await response.text();
      if (!response.ok) {
        onError(body || `HTTP ${response.status}`);
        return;
      }

      onSuccess(body);
    } catch (error) {
      onError(error?.message || "Profile request failed");
    }
  };

  window.flowWasmApiRequest = async function (method, path, apiUrl, authToken, contentType, body, onSuccess, onError) {
    try {
      const headers = { "Accept": "application/json" };
      if (authToken) {
        headers["Authorization"] = `Bearer ${authToken}`;
      }
      if (contentType) {
        headers["Content-Type"] = contentType;
      }

      const response = await fetch(`${apiUrl}${path}`, {
        method,
        headers,
        body: body ?? undefined
      });

      const text = await response.text();
      if (!response.ok) {
        onError(text || `HTTP ${response.status}`);
        return;
      }

      onSuccess(text);
    } catch (error) {
      onError(error?.message || `Request failed: ${method} ${path}`);
    }
  };

  const flowWasmPickedFiles = new Map();
  const flowWasmSockets = new Map();

  function makeFileId() {
    if (window.crypto && typeof window.crypto.randomUUID === "function") {
      return window.crypto.randomUUID();
    }
    return `flow-file-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }

  function serializePickedFile(file, id) {
    return {
      id,
      name: file.name || "image",
      mimeType: file.type || "application/octet-stream",
      sizeBytes: file.size || 0,
      previewUri: URL.createObjectURL(file)
    };
  }

  function buildImagePickerInput(multiple) {
    const input = document.createElement("input");
    input.type = "file";
    input.accept = "image/*";
    input.multiple = multiple;
    input.style.display = "none";
    document.body.appendChild(input);
    return input;
  }

  function makeRuntimeId(prefix) {
    if (window.crypto && typeof window.crypto.randomUUID === "function") {
      return `${prefix}-${window.crypto.randomUUID()}`;
    }
    return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }

  function buildWebSocketUrl(apiUrl, path, authQuery, extraParams) {
    const base = new URL(apiUrl);
    const protocol = base.protocol === "https:" ? "wss:" : "ws:";
    const normalizedBasePath = base.pathname.replace(/\/$/, "");
    const normalizedPath = path.startsWith("/") ? path : `/${path}`;
    const url = new URL(`${protocol}//${base.host}${normalizedBasePath}${normalizedPath}`);
    if (authQuery && authQuery.key && authQuery.value) {
      url.searchParams.set(authQuery.key, authQuery.value);
    }
    (extraParams || []).forEach(function (entry) {
      if (!entry) return;
      const value = entry.value;
      if (value === undefined || value === null || value === "") return;
      url.searchParams.set(entry.key, String(value));
    });
    return url.toString();
  }

  function registerSocket(handle, socket) {
    flowWasmSockets.set(handle, socket);
  }

  function unregisterSocket(handle) {
    flowWasmSockets.delete(handle);
  }

  function serializeSocketLifecycle(socket, wsUrl, extra) {
    return JSON.stringify({
      url: wsUrl,
      code: extra && typeof extra.code === "number" ? extra.code : null,
      reason: extra && extra.reason ? String(extra.reason) : null,
      wasClean: extra && typeof extra.wasClean === "boolean" ? extra.wasClean : null,
      readyState: socket ? socket.readyState : null,
      message: extra && extra.message ? String(extra.message) : null
    });
  }

  function resolveSocket(handle) {
    const socket = flowWasmSockets.get(handle);
    if (!socket) {
      throw new Error("Realtime socket handle is missing");
    }
    return socket;
  }

  window.flowWasmPickImages = function (maxItems, onSuccess, onError) {
    try {
      const input = buildImagePickerInput(true);
      input.onchange = function () {
        try {
          const files = Array.from(input.files || []).slice(0, Math.max(0, maxItems));
          const serialized = files.map(file => {
            const id = makeFileId();
            flowWasmPickedFiles.set(id, file);
            return serializePickedFile(file, id);
          });
          onSuccess(JSON.stringify(serialized));
        } catch (error) {
          onError(String(error));
        } finally {
          input.remove();
        }
      };
      input.click();
    } catch (error) {
      onError(String(error));
    }
  };

  window.flowWasmPickSingleImage = function (onSuccess, onError) {
    try {
      const input = buildImagePickerInput(false);
      input.onchange = function () {
        try {
          const file = (input.files || [])[0];
          if (!file) {
            onSuccess("null");
            return;
          }
          const id = makeFileId();
          flowWasmPickedFiles.set(id, file);
          onSuccess(JSON.stringify(serializePickedFile(file, id)));
        } catch (error) {
          onError(String(error));
        } finally {
          input.remove();
        }
      };
      input.click();
    } catch (error) {
      onError(String(error));
    }
  };

  window.flowWasmReadPickedFile = async function (fileId, onSuccess, onError) {
    try {
      const file = flowWasmPickedFiles.get(fileId);
      if (!file) {
        onError("Picked file is missing");
        return;
      }
      const buffer = await file.arrayBuffer();
      const bytes = new Uint8Array(buffer);
      let binary = "";
      const chunkSize = 0x8000;
      for (let index = 0; index < bytes.length; index += chunkSize) {
        const chunk = bytes.subarray(index, index + chunkSize);
        binary += String.fromCharCode.apply(null, Array.from(chunk));
      }
      onSuccess(JSON.stringify({
        name: file.name || "image",
        mimeType: file.type || "application/octet-stream",
        sizeBytes: file.size || 0,
        base64: btoa(binary)
      }));
    } catch (error) {
      onError(String(error));
    }
  };

  window.flowWasmUploadBase64 = async function (uploadUrl, contentType, base64, onSuccess, onError) {
    const uploadOrigin = (() => {
      try {
        return new URL(uploadUrl).origin;
      } catch (_error) {
        return uploadUrl;
      }
    })();
    try {
      const binary = atob(base64);
      const length = binary.length;
      const bytes = new Uint8Array(length);
      for (let index = 0; index < length; index += 1) {
        bytes[index] = binary.charCodeAt(index);
      }
      const response = await fetch(uploadUrl, {
        method: "PUT",
        headers: {
          "Content-Type": contentType
        },
        body: bytes
      });
      if (!response.ok) {
        const text = await response.text();
        onError(text || `Upload failed: ${response.status} (${uploadOrigin})`);
        return;
      }
      onSuccess();
    } catch (error) {
      const message = error && error.message ? error.message : String(error);
      onError(`Upload request failed for ${uploadOrigin}: ${message}`);
    }
  };

  window.flowWasmChatsRealtimeOpen = function (
    apiUrl,
    authQueryKey,
    authQueryValue,
    conversationId,
    afterSeq,
    replayLimit,
    onOpen,
    onEvent,
    onClosed,
    onError
  ) {
    try {
      const handle = makeRuntimeId("flow-chats-ws");
      const wsUrl = buildWebSocketUrl(apiUrl, "/chats/realtime/ws", {
        key: authQueryKey,
        value: authQueryValue
      }, [
        { key: "conversation_id", value: conversationId },
        { key: "after_seq", value: afterSeq > 0 ? afterSeq : null },
        { key: "replay_limit", value: replayLimit > 0 ? replayLimit : null }
      ]);
      const socket = new WebSocket(wsUrl);
      registerSocket(handle, socket);
      socket.onopen = function () {
        onOpen();
      };
      socket.onmessage = function (event) {
        onEvent(typeof event.data === "string" ? event.data : String(event.data));
      };
      socket.onerror = function () {
        onError(serializeSocketLifecycle(socket, wsUrl, {
          message: "Chats realtime websocket failed"
        }));
      };
      socket.onclose = function (event) {
        unregisterSocket(handle);
        onClosed(serializeSocketLifecycle(socket, wsUrl, event));
      };
      return handle;
    } catch (error) {
      onError(JSON.stringify({
        url: null,
        code: null,
        reason: null,
        wasClean: null,
        readyState: null,
        message: error && error.message ? error.message : String(error)
      }));
      return null;
    }
  };

  window.flowWasmChatsRealtimeSend = function (handle, payloadJson, onSuccess, onError) {
    try {
      const socket = resolveSocket(handle);
      if (socket.readyState !== WebSocket.OPEN) {
        onError("Chats realtime socket is not open");
        return;
      }
      socket.send(payloadJson);
      onSuccess();
    } catch (error) {
      onError(error && error.message ? error.message : String(error));
    }
  };

  window.flowWasmChatsRealtimeClose = function (handle) {
    try {
      const socket = flowWasmSockets.get(handle);
      if (!socket) return;
      unregisterSocket(handle);
      socket.close();
    } catch (_error) {
    }
  };

  window.flowWasmPresenceRealtimeOpen = function (
    apiUrl,
    authQueryKey,
    authQueryValue,
    onOpen,
    onEvent,
    onClosed,
    onError
  ) {
    try {
      const handle = makeRuntimeId("flow-presence-ws");
      const wsUrl = buildWebSocketUrl(apiUrl, "/presence/ws", {
        key: authQueryKey,
        value: authQueryValue
      });
      const socket = new WebSocket(wsUrl);
      registerSocket(handle, socket);
      socket.onopen = function () {
        onOpen();
      };
      socket.onmessage = function (event) {
        onEvent(typeof event.data === "string" ? event.data : String(event.data));
      };
      socket.onerror = function () {
        onError(serializeSocketLifecycle(socket, wsUrl, {
          message: "Presence realtime websocket failed"
        }));
      };
      socket.onclose = function (event) {
        unregisterSocket(handle);
        onClosed(serializeSocketLifecycle(socket, wsUrl, event));
      };
      return handle;
    } catch (error) {
      onError(JSON.stringify({
        url: null,
        code: null,
        reason: null,
        wasClean: null,
        readyState: null,
        message: error && error.message ? error.message : String(error)
      }));
      return null;
    }
  };

  window.flowWasmPresenceRealtimeSend = function (handle, payloadJson, onSuccess, onError) {
    try {
      const socket = resolveSocket(handle);
      if (socket.readyState !== WebSocket.OPEN) {
        onError("Presence realtime socket is not open");
        return;
      }
      socket.send(payloadJson);
      onSuccess();
    } catch (error) {
      onError(error && error.message ? error.message : String(error));
    }
  };

  window.flowWasmPresenceRealtimeClose = function (handle) {
    try {
      const socket = flowWasmSockets.get(handle);
      if (!socket) return;
      unregisterSocket(handle);
      socket.close();
    } catch (_error) {
    }
  };

  window.flowWasmCopyText = async function (text, onSuccess, onError) {
    try {
      if (!navigator.clipboard || !navigator.clipboard.writeText) {
        onError("Clipboard API is not available");
        return;
      }
      await navigator.clipboard.writeText(text);
      onSuccess();
    } catch (error) {
      onError(error && error.message ? error.message : String(error));
    }
  };
})();
