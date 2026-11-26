import fs from 'fs';
import path from 'path';
import crypto from 'crypto';
import express, { type Request, type Response } from 'express';
import dotenv from 'dotenv';
import JSZip from 'jszip';
import {
  GoogleGenAI,
  ThinkingLevel,
  Type,
  type FunctionDeclaration,
} from '@google/genai';

dotenv.config();

const PORT = Number(process.env.PORT || 3000);
const ROOT_DIR = process.cwd();
const ANDROID_DIR = path.resolve(ROOT_DIR, 'android');

const ai = new GoogleGenAI({
  apiKey: process.env.GEMINI_API_KEY,
  httpOptions: {
    headers: {
      'User-Agent': 'aistudio-build',
    },
  },
});

const PHONE_ACTION_DECLARATIONS: FunctionDeclaration[] = [
  {
    name: 'open_app',
    description:
      'Open an installed Android application by its common name (e.g., YouTube, WhatsApp, Chrome, Settings, Camera, Spotify).',
    parameters: {
      type: Type.OBJECT,
      properties: {
        app_name: {
          type: Type.STRING,
          description: 'Human-readable name of the Android app to launch.',
        },
      },
      required: ['app_name'],
    },
  },
  {
    name: 'launch_dialer',
    description:
      'Open the Android phone dialer with an optional phone number ready for the user to place a call.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        phone_number: {
          type: Type.STRING,
          description: 'Digits or phone number to pre-fill in the dialer, or empty string to open the dialer.',
        },
      },
      required: ['phone_number'],
    },
  },
  {
    name: 'open_website',
    description: 'Open a website URL in the user default Android web browser.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        url: {
          type: Type.STRING,
          description: 'Full HTTPS website URL or domain name to open.',
        },
      },
      required: ['url'],
    },
  },
  {
    name: 'start_navigation',
    description: 'Start turn-by-turn navigation in Maps to a destination address or place.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        destination: {
          type: Type.STRING,
          description: 'Destination name, landmark, or street address.',
        },
      },
      required: ['destination'],
    },
  },
  {
    name: 'set_alarm',
    description: 'Set an alarm on the user Android clock app using 24-hour time.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        hour: {
          type: Type.INTEGER,
          description: 'Hour in 24-hour format from 0 to 23.',
        },
        minute: {
          type: Type.INTEGER,
          description: 'Minute from 0 to 59.',
        },
        label: {
          type: Type.STRING,
          description: 'Optional short alarm label.',
        },
      },
      required: ['hour', 'minute'],
    },
  },
];

const JARVIS_SYSTEM_INSTRUCTION = `You are JARVIS, a personal hands-free voice assistant running on the user's Android phone (Redmi A5).
Personality: intelligent, helpful, concise, calm, and friendly.
Voice formatting rules:
- Your responses are spoken aloud by Android Text-to-Speech. Keep answers natural, clear, and concise (usually 1 to 3 sentences unless the user asks for detailed coding, research, or planning).
- Never use markdown formatting like **bold**, bullet asterisks, or backticks in your spoken reply.
- Always be strictly honest. Never claim to perform a phone or real-world action that you did not actually trigger via a tool call.
- If the user asks to perform an unsupported phone control (such as toggling Wi-Fi, reading private SMS, or making a silent background call), honestly explain that Android restricts that action and offer what you can do instead.
- Use the provided phone action functions ONLY when the user clearly asks to open an app, launch the dialer, open a website, start navigation, or set an alarm.`;

interface TurnHistoryItem {
  role: 'user' | 'model';
  text: string;
}

interface ParsedPhoneAction {
  type: 'OPEN_APP' | 'LAUNCH_DIALER' | 'OPEN_WEBSITE' | 'START_NAVIGATION' | 'SET_ALARM';
  target: string;
  summary: string;
  hour?: number;
  minute?: number;
  sensitive: boolean;
}

function verifyOptionalBearerToken(req: Request): boolean {
  const configuredToken = (process.env.JARVIS_ACCESS_TOKEN || '').trim();
  if (!configuredToken) {
    return true;
  }
  const header = req.headers.authorization || '';
  if (!header.startsWith('Bearer ')) {
    return false;
  }
  const supplied = header.slice('Bearer '.length).trim();
  const a = Buffer.from(supplied, 'utf8');
  const b = Buffer.from(configuredToken, 'utf8');
  if (a.length !== b.length) {
    return false;
  }
  return crypto.timingSafeEqual(a, b);
}

function mapFunctionCallToPhoneAction(
  name: string,
  args: Record<string, unknown> | undefined
): ParsedPhoneAction | null {
  const safeArgs = args || {};
  switch (name) {
    case 'open_app': {
      const appName = typeof safeArgs.app_name === 'string' ? safeArgs.app_name.trim() : '';
      if (!appName) return null;
      return {
        type: 'OPEN_APP',
        target: appName,
        summary: `Open app "${appName}"`,
        sensitive: false,
      };
    }
    case 'launch_dialer': {
      const phoneNumber =
        typeof safeArgs.phone_number === 'string' ? safeArgs.phone_number.trim() : '';
      return {
        type: 'LAUNCH_DIALER',
        target: phoneNumber,
        summary: phoneNumber ? `Open dialer for ${phoneNumber}` : 'Open phone dialer',
        sensitive: true,
      };
    }
    case 'open_website': {
      const url = typeof safeArgs.url === 'string' ? safeArgs.url.trim() : '';
      if (!url) return null;
      return {
        type: 'OPEN_WEBSITE',
        target: url,
        summary: `Open website ${url}`,
        sensitive: true,
      };
    }
    case 'start_navigation': {
      const destination =
        typeof safeArgs.destination === 'string' ? safeArgs.destination.trim() : '';
      if (!destination) return null;
      return {
        type: 'START_NAVIGATION',
        target: destination,
        summary: `Navigate to ${destination}`,
        sensitive: false,
      };
    }
    case 'set_alarm': {
      const hour = typeof safeArgs.hour === 'number' ? Math.trunc(safeArgs.hour) : NaN;
      const minute = typeof safeArgs.minute === 'number' ? Math.trunc(safeArgs.minute) : NaN;
      const label = typeof safeArgs.label === 'string' ? safeArgs.label.trim() : '';
      if (!Number.isInteger(hour) || hour < 0 || hour > 23) return null;
      if (!Number.isInteger(minute) || minute < 0 || minute > 59) return null;
      const mm = String(minute).padStart(2, '0');
      return {
        type: 'SET_ALARM',
        target: label,
        hour,
        minute,
        summary: label ? `Set alarm for ${hour}:${mm} (${label})` : `Set alarm for ${hour}:${mm}`,
        sensitive: true,
      };
    }
    default:
      return null;
  }
}

function collectAndroidFiles(dir: string, baseDir: string): Array<{ path: string; size: number }> {
  const results: Array<{ path: string; size: number }> = [];
  if (!fs.existsSync(dir)) return results;

  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === 'build' || entry.name === '.gradle' || entry.name === '.idea') continue;
      results.push(...collectAndroidFiles(fullPath, baseDir));
    } else if (entry.isFile()) {
      const rel = path.relative(baseDir, fullPath).replace(/\\/g, '/');
      const stat = fs.statSync(fullPath);
      results.push({ path: rel, size: stat.size });
    }
  }
  return results.sort((a, b) => a.path.localeCompare(b.path));
}

async function startServer() {
  const app = express();
  app.use(express.json({ limit: '256kb' }));

  app.get('/api/jarvis/health', (_req: Request, res: Response) => {
    res.json({
      ok: true,
      service: 'jarvis-gemini-backend',
      model: 'gemini-4-hydrogen',
      geminiConfigured: Boolean(process.env.GEMINI_API_KEY),
      tokenProtected: Boolean((process.env.JARVIS_ACCESS_TOKEN || '').trim()),
    });
  });

  app.post('/api/jarvis/turn', async (req: Request, res: Response) => {
    if (!verifyOptionalBearerToken(req)) {
      res.status(401).json({ error: 'Unauthorized: invalid or missing access token.' });
      return;
    }

    if (!process.env.GEMINI_API_KEY) {
      res.status(503).json({
        error: 'GEMINI_API_KEY is not configured on the backend. Add it in Settings > Secrets.',
      });
      return;
    }

    const body = req.body as {
      message?: unknown;
      history?: unknown;
      device?: unknown;
    };

    const rawMessage = typeof body.message === 'string' ? body.message.trim() : '';
    if (!rawMessage || rawMessage.length > 2000) {
      res.status(400).json({ error: 'A non-empty message up to 2000 characters is required.' });
      return;
    }

    const sanitizedHistory: TurnHistoryItem[] = [];
    if (Array.isArray(body.history)) {
      for (const item of body.history.slice(-14)) {
        if (
          item &&
          typeof item === 'object' &&
          (item.role === 'user' || item.role === 'model') &&
          typeof item.text === 'string' &&
          item.text.trim().length > 0
        ) {
          sanitizedHistory.push({
            role: item.role,
            text: item.text.trim().slice(0, 2000),
          });
        }
      }
    }

    const contents = [
      ...sanitizedHistory.map((turn) => ({
        role: turn.role,
        parts: [{ text: turn.text }],
      })),
      {
        role: 'user' as const,
        parts: [{ text: rawMessage }],
      },
    ];

    try {
      const response = await ai.models.generateContent({
        model: 'gemini-4-hydrogen',
        contents,
        config: {
          systemInstruction: JARVIS_SYSTEM_INSTRUCTION,
          thinkingConfig: { thinkingLevel: ThinkingLevel.LOW },
          tools: [{ functionDeclarations: PHONE_ACTION_DECLARATIONS }],
        },
      });

      const functionCall = response.functionCalls?.[0];
      let action: ParsedPhoneAction | null = null;

      if (functionCall?.name) {
        action = mapFunctionCallToPhoneAction(
          functionCall.name,
          functionCall.args as Record<string, unknown> | undefined
        );
      }

      let replyText = (response.text || '').trim();
      if (!replyText && action) {
        replyText = `Preparing to ${action.summary.toLowerCase()}.`;
      }
      if (!replyText) {
        replyText = 'I heard you, but I did not receive a clear response. Could you say that again?';
      }

      res.json({
        reply: replyText,
        action,
      });
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Upstream Gemini error';
      const status =
        message.includes('429') || message.includes('RESOURCE_EXHAUSTED')
          ? 429
          : message.includes('403') || message.includes('PERMISSION_DENIED')
            ? 403
            : 502;
      res.status(status).json({
        error:
          status === 429
            ? 'Gemini quota limit reached. Please wait briefly and try again.'
            : status === 403
              ? 'Backend Gemini API key was rejected. Check Settings > Secrets.'
              : 'Unable to reach Gemini AI right now.',
      });
    }
  });

  app.get('/api/android/files', (_req: Request, res: Response) => {
    const files = collectAndroidFiles(ANDROID_DIR, ANDROID_DIR);
    res.json({
      root: 'android',
      files,
      appUrl: process.env.APP_URL || '',
    });
  });

  app.get('/api/android/file', (req: Request, res: Response) => {
    const relPath = typeof req.query.path === 'string' ? req.query.path : '';
    if (!relPath) {
      res.status(400).json({ error: 'Missing path parameter.' });
      return;
    }

    const resolved = path.resolve(ANDROID_DIR, relPath);
    if (!resolved.startsWith(ANDROID_DIR + path.sep) || !fs.existsSync(resolved)) {
      res.status(404).json({ error: 'File not found.' });
      return;
    }

    const stat = fs.statSync(resolved);
    if (!stat.isFile()) {
      res.status(400).json({ error: 'Target is not a file.' });
      return;
    }

    const content = fs.readFileSync(resolved, 'utf8');
    res.json({ path: relPath, content });
  });

  app.get('/api/android/download-zip', async (req: Request, res: Response) => {
    if (!fs.existsSync(ANDROID_DIR)) {
      res.status(404).json({ error: 'Android project directory not found.' });
      return;
    }

    const zip = new JSZip();
    const rootFolder = zip.folder('jarvis-redmi-a5-android');
    const files = collectAndroidFiles(ANDROID_DIR, ANDROID_DIR);

    const requestOrigin = `${req.protocol}://${req.get('host')}`;
    const liveBackendUrl = (process.env.APP_URL || requestOrigin).replace(/\/$/, '');

    for (const fileInfo of files) {
      const fullPath = path.resolve(ANDROID_DIR, fileInfo.path);
      if (!fullPath.startsWith(ANDROID_DIR + path.sep)) continue;
      let content = fs.readFileSync(fullPath, 'utf8');

      // Inject the user's live backend URL cleanly if APP_URL is active
      if (fileInfo.path === 'app/build.gradle.kts' && liveBackendUrl.startsWith('https://')) {
        content = content.replace(
          'https://ais-pre-dvzwnnsggrni2ygusxevbv-490325094984.asia-southeast1.run.app',
          liveBackendUrl
        );
      }

      rootFolder?.file(fileInfo.path, content);
    }

    const buffer = await zip.generateAsync({ type: 'nodebuffer', compression: 'DEFLATE' });
    res.setHeader('Content-Type', 'application/zip');
    res.setHeader('Content-Disposition', 'attachment; filename="jarvis-redmi-a5-android.zip"');
    res.send(buffer);
  });

  if (process.env.NODE_ENV !== 'production') {
    const { createServer: createViteServer } = await import('vite');
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    const distDir = path.resolve(ROOT_DIR, 'dist');
    app.use(express.static(distDir));
    app.get('*', (_req: Request, res: Response) => {
      res.sendFile(path.join(distDir, 'index.html'));
    });
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`JARVIS server listening on http://0.0.0.0:${PORT}`);
  });
}

startServer().catch((err) => {
  console.error('Failed to start JARVIS server:', err);
  process.exit(1);
});
