import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Mic, MicOff, Send, Volume2 } from 'lucide-react';
import { ReactorSvg, type AssistantStatus } from './components/ReactorSvg';
import { AndroidExplorer } from './components/AndroidExplorer';
import { RedmiSetupGuide } from './components/RedmiSetupGuide';

type ActiveTab = 'console' | 'source' | 'guide';
type ListeningMode = 'wake' | 'continuous';

interface ChatTurn {
  role: 'user' | 'model';
  text: string;
}

interface PhoneActionPreview {
  type: 'OPEN_APP' | 'LAUNCH_DIALER' | 'OPEN_WEBSITE' | 'START_NAVIGATION' | 'SET_ALARM';
  target: string;
  summary: string;
  hour?: number;
  minute?: number;
  sensitive: boolean;
}

interface SpeechRecognitionAlternativeLike {
  transcript: string;
}

interface SpeechRecognitionResultLike {
  isFinal: boolean;
  0: SpeechRecognitionAlternativeLike;
}

interface SpeechRecognitionEventLike {
  resultIndex: number;
  results: ArrayLike<SpeechRecognitionResultLike>;
}

interface SpeechRecognitionErrorEventLike {
  error: string;
}

interface SpeechRecognitionInstance {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  onresult: ((event: SpeechRecognitionEventLike) => void) | null;
  onerror: ((event: SpeechRecognitionErrorEventLike) => void) | null;
  onend: (() => void) | null;
  start: () => void;
  stop: () => void;
  abort: () => void;
}

declare global {
  interface Window {
    SpeechRecognition?: new () => SpeechRecognitionInstance;
    webkitSpeechRecognition?: new () => SpeechRecognitionInstance;
  }
}

export default function App() {
  const [activeTab, setActiveTab] = useState<ActiveTab>('console');
  const [status, setStatus] = useState<AssistantStatus>('OFFLINE');
  const [listeningMode, setListeningMode] = useState<ListeningMode>('wake');
  const [isMicActive, setIsMicActive] = useState<boolean>(false);
  const [audioLevel, setAudioLevel] = useState<number>(0);
  const [partialText, setPartialText] = useState<string>('');
  const [textInput, setTextInput] = useState<string>('');
  const [history, setHistory] = useState<ChatTurn[]>([]);
  const [pendingAction, setPendingAction] = useState<PhoneActionPreview | null>(null);
  const [notice, setNotice] = useState<string>('');
  const [speechSupported, setSpeechSupported] = useState<boolean>(true);

  const isRunningRef = useRef<boolean>(false);
  const isSpeakingRef = useRef<boolean>(false);
  const modeRef = useRef<ListeningMode>('wake');
  const conversationWindowUntilRef = useRef<number>(0);
  const historyRef = useRef<ChatTurn[]>([]);
  const recognitionRef = useRef<SpeechRecognitionInstance | null>(null);

  useEffect(() => {
    modeRef.current = listeningMode;
  }, [listeningMode]);

  useEffect(() => {
    historyRef.current = history;
  }, [history]);

  useEffect(() => {
    const SpeechRec = window.SpeechRecognition || window.webkitSpeechRecognition;
    setSpeechSupported(Boolean(SpeechRec));
    return () => {
      isRunningRef.current = false;
      recognitionRef.current?.abort();
      window.speechSynthesis?.cancel();
    };
  }, []);

  const stopRecognition = useCallback(() => {
    if (recognitionRef.current) {
      recognitionRef.current.onend = null;
      try {
        recognitionRef.current.abort();
      } catch {
        // Ignore already stopped recognizer
      }
      recognitionRef.current = null;
    }
    setIsMicActive(false);
    setAudioLevel(0);
  }, []);

  const startRecognitionCycle = useCallback(() => {
    if (!isRunningRef.current || isSpeakingRef.current) return;

    const SpeechRec = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SpeechRec) {
      setStatus('STANDBY');
      setNotice('Browser Web Speech API is unavailable here. Use text input below or run the native Android APK.');
      return;
    }

    stopRecognition();

    const rec = new SpeechRec();
    rec.lang = 'en-US';
    rec.continuous = false;
    rec.interimResults = true;

    const inWindow = Date.now() < conversationWindowUntilRef.current;
    setStatus(modeRef.current === 'wake' && !inWindow ? 'STANDBY' : 'LISTENING');
    setIsMicActive(true);

    rec.onresult = (event) => {
      let interim = '';
      let finalTranscript = '';

      for (let i = event.resultIndex; i < event.results.length; i++) {
        const res = event.results[i];
        const txt = res[0]?.transcript || '';
        if (res.isFinal) {
          finalTranscript += txt;
        } else {
          interim += txt;
        }
      }

      if (interim.trim()) {
        setPartialText(interim.trim());
        setAudioLevel(0.65);
      }

      if (finalTranscript.trim()) {
        setPartialText('');
        setAudioLevel(0);
        void handleUserUtterance(finalTranscript.trim(), true);
      }
    };

    rec.onerror = (event) => {
      setIsMicActive(false);
      setAudioLevel(0);
      if (!isRunningRef.current) return;
      if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
        isRunningRef.current = false;
        setStatus('OFFLINE');
        setNotice('Microphone permission was denied in the browser. You can still type below or run the Android APK.');
        return;
      }
      if (event.error === 'no-speech' || event.error === 'aborted') {
        setTimeout(() => startRecognitionCycle(), 300);
        return;
      }
      setNotice(`Speech recognition notice: ${event.error}`);
      setTimeout(() => startRecognitionCycle(), 1200);
    };

    rec.onend = () => {
      setIsMicActive(false);
      setAudioLevel(0);
      if (isRunningRef.current && !isSpeakingRef.current) {
        setTimeout(() => startRecognitionCycle(), 260);
      }
    };

    recognitionRef.current = rec;
    try {
      rec.start();
    } catch {
      setIsMicActive(false);
    }
  }, [stopRecognition]);

  const speakText = useCallback(
    (text: string) => {
      stopRecognition();
      if (!window.speechSynthesis) {
        if (isRunningRef.current) {
          conversationWindowUntilRef.current = Date.now() + 20000;
          startRecognitionCycle();
        }
        return;
      }

      isSpeakingRef.current = true;
      setStatus('SPEAKING');
      window.speechSynthesis.cancel();

      const utterance = new SpeechSynthesisUtterance(text);
      utterance.lang = 'en-US';
      utterance.pitch = 0.94;
      utterance.rate = 1.02;

      const voices = window.speechSynthesis.getVoices();
      const usMale = voices.find(
        (v) =>
          v.lang.startsWith('en-US') &&
          /david|mark|alex|daniel|guy|male|google us english/i.test(v.name)
      );
      if (usMale) {
        utterance.voice = usMale;
      }

      utterance.onend = () => {
        isSpeakingRef.current = false;
        if (isRunningRef.current) {
          conversationWindowUntilRef.current = Date.now() + 20000;
          setTimeout(() => startRecognitionCycle(), 350);
        } else {
          setStatus('OFFLINE');
        }
      };

      utterance.onerror = () => {
        isSpeakingRef.current = false;
        if (isRunningRef.current) {
          setTimeout(() => startRecognitionCycle(), 400);
        } else {
          setStatus('OFFLINE');
        }
      };

      window.speechSynthesis.speak(utterance);
    },
    [startRecognitionCycle, stopRecognition]
  );

  const handleUserUtterance = async (rawText: string, fromVoice: boolean) => {
    let promptText = rawText.trim();
    if (!promptText) return;

    if (fromVoice && modeRef.current === 'wake' && Date.now() > conversationWindowUntilRef.current) {
      const wakeMatch = /\b(hey\s+)?jarvis\b[:,]?\s*/i;
      if (!wakeMatch.test(promptText)) {
        return;
      }
      promptText = promptText.replace(wakeMatch, '').trim();
      if (!promptText) {
        conversationWindowUntilRef.current = Date.now() + 20000;
        speakText("Yes, I'm listening.");
        return;
      }
    }

    stopRecognition();
    setStatus('THINKING');
    setNotice('');

    const currentHistory = historyRef.current;
    setHistory((prev) => [...prev.slice(-14), { role: 'user', text: promptText }]);

    try {
      const response = await fetch('/api/jarvis/turn', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          message: promptText,
          history: currentHistory.slice(-12),
          device: 'Web Companion',
        }),
      });

      const data = (await response.json()) as {
        reply?: string;
        action?: PhoneActionPreview | null;
        error?: string;
      };

      if (!response.ok || !data.reply) {
        const errText = data.error || 'Unable to reach the JARVIS backend.';
        setNotice(errText);
        setHistory((prev) => [...prev, { role: 'model', text: errText }]);
        if (fromVoice) {
          speakText(errText);
        } else {
          setStatus(isRunningRef.current ? 'STANDBY' : 'OFFLINE');
        }
        return;
      }

      setHistory((prev) => [...prev, { role: 'model', text: data.reply! }]);
      if (data.action) {
        setPendingAction(data.action);
      } else {
        setPendingAction(null);
      }

      if (fromVoice || isRunningRef.current) {
        speakText(data.reply);
      } else {
        setStatus('OFFLINE');
      }
    } catch {
      const fallback = 'Network error reaching the JARVIS backend.';
      setNotice(fallback);
      setStatus(isRunningRef.current ? 'STANDBY' : 'OFFLINE');
    }
  };

  const handleTogglePower = () => {
    if (isRunningRef.current || status !== 'OFFLINE') {
      isRunningRef.current = false;
      isSpeakingRef.current = false;
      stopRecognition();
      window.speechSynthesis?.cancel();
      setPartialText('');
      setPendingAction(null);
      setStatus('OFFLINE');
    } else {
      isRunningRef.current = true;
      conversationWindowUntilRef.current = Date.now() + 20000;
      setNotice('');
      startRecognitionCycle();
    }
  };

  const handleTextSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = textInput.trim();
    if (!trimmed) return;
    setTextInput('');
    void handleUserUtterance(trimmed, false);
  };

  return (
    <div className="min-h-screen bg-[#030712] text-slate-100 flex flex-col">
      {/* Three-Zone Top Bar */}
      <header className="border-b border-slate-800/80 bg-[#030712]">
        <div className="max-w-6xl mx-auto px-6 h-16 flex items-center justify-between gap-8">
          <button
            type="button"
            onClick={() => setActiveTab('console')}
            className="font-display text-lg font-bold tracking-[0.18em] text-slate-100 whitespace-nowrap shrink-0"
          >
            JARVIS
          </button>

          <nav className="flex items-center gap-6 text-sm font-medium">
            <button
              type="button"
              onClick={() => setActiveTab('console')}
              className={`py-1 border-b-2 transition-colors whitespace-nowrap shrink-0 ${
                activeTab === 'console'
                  ? 'border-cyan-400 text-slate-100'
                  : 'border-transparent text-slate-400 hover:text-slate-200'
              }`}
            >
              Voice Console
            </button>
            <button
              type="button"
              onClick={() => setActiveTab('source')}
              className={`py-1 border-b-2 transition-colors whitespace-nowrap shrink-0 ${
                activeTab === 'source'
                  ? 'border-cyan-400 text-slate-100'
                  : 'border-transparent text-slate-400 hover:text-slate-200'
              }`}
            >
              Android Source
            </button>
            <button
              type="button"
              onClick={() => setActiveTab('guide')}
              className={`py-1 border-b-2 transition-colors whitespace-nowrap shrink-0 ${
                activeTab === 'guide'
                  ? 'border-cyan-400 text-slate-100'
                  : 'border-transparent text-slate-400 hover:text-slate-200'
              }`}
            >
              Redmi A5 Guide
            </button>
          </nav>

          <a
            href="/api/android/download-zip"
            className="px-4 py-2 rounded-lg bg-cyan-400 text-slate-950 text-xs font-semibold hover:bg-cyan-300 transition-colors whitespace-nowrap shrink-0"
          >
            Download APK Project
          </a>
        </div>
      </header>

      <main className="flex-1 max-w-6xl w-full mx-auto px-6 py-8">
        {activeTab === 'console' && (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-stretch">
            {/* Left Primary Reactor & Power Control */}
            <section className="lg:col-span-5 rounded-2xl border border-slate-800/80 bg-[#0B132B]/70 p-6 flex flex-col items-center justify-between gap-6">
              <div className="w-full flex items-center justify-between text-xs text-slate-400">
                <span className="inline-flex items-center gap-1.5">
                  {isMicActive ? (
                    <Mic className="w-3.5 h-3.5 text-cyan-400" />
                  ) : (
                    <MicOff className="w-3.5 h-3.5 text-slate-500" />
                  )}
                  {isMicActive ? 'Microphone active' : 'Microphone off'}
                </span>
                <div className="inline-flex items-center gap-1 bg-[#030712] p-1 rounded-lg border border-slate-800">
                  <button
                    type="button"
                    onClick={() => setListeningMode('wake')}
                    className={`px-2.5 py-1 rounded-md text-xs transition-colors whitespace-nowrap ${
                      listeningMode === 'wake'
                        ? 'bg-cyan-500/20 text-cyan-300'
                        : 'text-slate-400 hover:text-slate-200'
                    }`}
                  >
                    Hey JARVIS
                  </button>
                  <button
                    type="button"
                    onClick={() => setListeningMode('continuous')}
                    className={`px-2.5 py-1 rounded-md text-xs transition-colors whitespace-nowrap ${
                      listeningMode === 'continuous'
                        ? 'bg-cyan-500/20 text-cyan-300'
                        : 'text-slate-400 hover:text-slate-200'
                    }`}
                  >
                    Continuous
                  </button>
                </div>
              </div>

              <div className="flex flex-col items-center">
                <ReactorSvg status={status} audioLevel={audioLevel} />
                <div className="mt-3 font-display text-lg font-semibold tracking-[0.25em] text-cyan-300">
                  {status}
                </div>
                <p className="mt-1.5 text-xs text-slate-400 text-center max-w-xs">
                  {status === 'OFFLINE'
                    ? 'Test the live Gemini AI voice loop here, or download the native Kotlin APK for background screen-off operation on your Redmi A5.'
                    : listeningMode === 'wake' && status === 'STANDBY'
                      ? 'Say "Hey JARVIS" to begin speaking.'
                      : 'Listening in US English with automatic turn-taking.'}
                </p>
              </div>

              {partialText && (
                <div className="w-full rounded-lg border border-slate-800 bg-[#030712] px-3.5 py-2.5 text-xs text-cyan-200 text-center">
                  &ldquo;{partialText}&rdquo;
                </div>
              )}

              <button
                type="button"
                onClick={handleTogglePower}
                className={`w-full py-3.5 rounded-xl font-display text-sm font-bold tracking-[0.18em] transition-colors ${
                  status === 'OFFLINE'
                    ? 'bg-cyan-400 text-slate-950 hover:bg-cyan-300'
                    : 'bg-red-500 text-white hover:bg-red-400'
                }`}
              >
                {status === 'OFFLINE' ? 'TURN ON' : 'TURN OFF'}
              </button>
            </section>

            {/* Right Live Conversation & Action Intent Verification */}
            <section className="lg:col-span-7 rounded-2xl border border-slate-800/80 bg-[#0B132B]/40 p-6 flex flex-col justify-between gap-4 min-h-[500px]">
              <div className="flex items-center justify-between border-b border-slate-800/80 pb-3.5">
                <div>
                  <h1 className="font-display text-lg font-semibold text-slate-100">
                    JARVIS Backend &amp; Voice Test Console
                  </h1>
                  <p className="text-xs text-slate-400">
                    Connected to secure server-side Gemini AI. Native phone intents execute on Android.
                  </p>
                </div>
                <Volume2 className="w-4 h-4 text-cyan-400 shrink-0" />
              </div>

              {notice && (
                <div className="rounded-lg border border-amber-500/40 bg-amber-950/30 px-3.5 py-2.5 text-xs text-amber-200">
                  {notice}
                </div>
              )}

              <div className="flex-1 overflow-y-auto space-y-3 max-h-[340px] pr-1">
                {history.length === 0 ? (
                  <div className="h-full flex flex-col items-center justify-center text-center py-10 text-slate-400">
                    <p className="text-sm font-medium text-slate-300">No conversation yet</p>
                    <p className="mt-1 text-xs max-w-md text-slate-400">
                      Press TURN ON to speak using your browser microphone, or type a prompt below like &ldquo;Set an alarm for 6:30 AM&rdquo; or &ldquo;Open YouTube&rdquo;.
                    </p>
                  </div>
                ) : (
                  history.map((turn, idx) => (
                    <div
                      key={idx}
                      className={`flex ${turn.role === 'user' ? 'justify-end' : 'justify-start'}`}
                    >
                      <div
                        className={`max-w-[85%] rounded-xl px-4 py-2.5 text-sm leading-relaxed ${
                          turn.role === 'user'
                            ? 'bg-cyan-500/15 border border-cyan-500/30 text-slate-100'
                            : 'bg-[#030712] border border-slate-800 text-slate-200'
                        }`}
                      >
                        {turn.text}
                      </div>
                    </div>
                  ))
                )}
              </div>

              {pendingAction && (
                <div className="rounded-xl border border-amber-500/40 bg-[#030712] p-3.5 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                  <div>
                    <p className="text-xs font-semibold text-amber-300">
                      Android Action Intent Queued ({pendingAction.type})
                    </p>
                    <p className="text-xs text-slate-300 mt-0.5">{pendingAction.summary}</p>
                  </div>
                  <button
                    type="button"
                    onClick={() => setPendingAction(null)}
                    className="px-3 py-1.5 rounded-lg border border-slate-700 text-xs text-slate-300 hover:bg-slate-800 whitespace-nowrap shrink-0"
                  >
                    Dismiss Preview
                  </button>
                </div>
              )}

              <form onSubmit={handleTextSubmit} className="flex items-center gap-2 pt-2 border-t border-slate-800/80">
                <input
                  type="text"
                  value={textInput}
                  onChange={(e) => setTextInput(e.target.value)}
                  placeholder={
                    speechSupported
                      ? 'Ask JARVIS anything or test an Android action...'
                      : 'Type to talk to JARVIS (Web Speech unavailable in this browser)...'
                  }
                  className="flex-1 bg-[#030712] border border-slate-800 rounded-xl px-4 py-2.5 text-sm text-slate-100 placeholder:text-slate-500 focus:outline-none focus:border-cyan-500"
                />
                <button
                  type="submit"
                  disabled={!textInput.trim() || status === 'THINKING'}
                  className="px-4 py-2.5 rounded-xl bg-cyan-400 text-slate-950 font-semibold text-sm hover:bg-cyan-300 disabled:opacity-40 transition-colors inline-flex items-center gap-1.5 whitespace-nowrap shrink-0"
                >
                  <Send className="w-4 h-4" />
                  Send
                </button>
              </form>
            </section>
          </div>
        )}

        {activeTab === 'source' && <AndroidExplorer />}
        {activeTab === 'guide' && <RedmiSetupGuide />}
      </main>
    </div>
  );
}
