# QuizCounter

Separate Android Admin and User apps with a real-time WebSocket relay.

## Build APKs from your phone

Open **Actions** → **Build QuizCounter APKs** → **Run workflow**.

When it finishes, open the successful run and download the **QuizCounter-APKs** artifact. It contains the Admin and User debug APKs.

## Architecture

Admin app ⇄ Internet ⇄ WebSocket relay ⇄ User apps

The relay forwards messages and does not persist questions or answers.

## Relay

```bash
cd relay
npm install
RELAY_TOKEN=change-this-token npm start
```

Use a TLS WebSocket URL such as `wss://your-host/ws` in both apps.

## Current prototype

- Admin starts/stops the live counter.
- Admin broadcasts questions.
- Users submit answers.
- Room codes isolate sessions.
- User and Admin are separate Android applications.

Do not publish the sample relay token in production.
