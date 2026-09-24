import http from "node:http";
import crypto from "node:crypto";
import { WebSocketServer } from "ws";

const PORT = Number(process.env.PORT || 8080);
const RELAY_TOKEN = process.env.RELAY_TOKEN || "CHANGE_ME";
const rooms = new Map();

const send = (ws, obj) => {
  if (ws.readyState === 1) ws.send(JSON.stringify(obj));
};

const server = http.createServer((req, res) => {
  res.writeHead(200, {"content-type":"application/json"});
  res.end(JSON.stringify({ok:true, service:"quizcounter-relay"}));
});

const wss = new WebSocketServer({server, path:"/ws"});

wss.on("connection", ws => {
  ws.id = crypto.randomUUID();
  ws.room = null;
  ws.role = null;

  ws.on("message", raw => {
    let msg;
    try { msg = JSON.parse(raw.toString()); }
    catch { return send(ws, {type:"error", message:"Invalid JSON"}); }

    if (msg.type === "join") {
      if (msg.token !== RELAY_TOKEN) return send(ws, {type:"error", message:"Invalid relay token"});
      const room = String(msg.room || "").trim().toUpperCase();
      const role = msg.role === "admin" ? "admin" : "user";
      if (!/^[A-Z0-9-]{4,32}$/.test(room)) return send(ws, {type:"error", message:"Invalid room"});

      if (ws.room && rooms.has(ws.room)) rooms.get(ws.room).delete(ws);
      if (!rooms.has(room)) rooms.set(room, new Set());
      rooms.get(room).add(ws);
      ws.room = room;
      ws.role = role;
      return send(ws, {type:"joined", room, role});
    }

    if (!ws.room) return send(ws, {type:"error", message:"Join a room first"});
    const peers = rooms.get(ws.room) || new Set();

    for (const peer of peers) {
      if (peer !== ws && peer.readyState === 1) {
        peer.send(JSON.stringify({...msg, from:ws.role, senderId:ws.id}));
      }
    }
  });

  ws.on("close", () => {
    if (ws.room && rooms.has(ws.room)) {
      const set = rooms.get(ws.room);
      set.delete(ws);
      if (!set.size) rooms.delete(ws.room);
    }
  });
});

server.listen(PORT, () => console.log("QuizCounter relay listening on " + PORT));
