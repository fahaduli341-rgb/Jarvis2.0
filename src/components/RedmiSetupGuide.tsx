import React from 'react';
import { BatteryCharging, CheckCircle2, Download, ShieldAlert, Smartphone, Terminal } from 'lucide-react';

interface CapabilityRow {
  feature: string;
  support: 'Full' | 'Requires Permission' | 'Android Limited';
  detail: string;
}

const CAPABILITY_MATRIX: CapabilityRow[] = [
  {
    feature: 'Foreground Microphone Service',
    support: 'Full',
    detail:
      'Runs continuously with foregroundServiceType="microphone" and persistent notification "JARVIS is active — Voice assistant running" plus a one-tap Turn Off action.',
  },
  {
    feature: 'Background & Other-App Voice Chat',
    support: 'Requires Permission',
    detail:
      'Continues listening while you switch to other apps once RECORD_AUDIO and POST_NOTIFICATIONS are granted and battery optimization is set to Unrestricted.',
  },
  {
    feature: 'Screen-Off & Pocket Listening',
    support: 'Android Limited',
    detail:
      'Holds a partial WakeLock and restarts recognition cycles. However, Android 12–15 and Xiaomi HyperOS/MIUI may pause cloud SpeechRecognizer during deep Doze or show privacy indicators unless Battery Saver is set to "No restrictions".',
  },
  {
    feature: 'Always-On Offline DSP Hotword',
    support: 'Android Limited',
    detail:
      'Hardware AlwaysOnHotwordDetector is restricted to system-privileged assistant apps. JARVIS solves this with a Wake-Phrase filter mode ("Hey JARVIS") and a Continuous Hands-Free mode.',
  },
  {
    feature: 'Open Apps, Dialer, Web, Maps, Alarms',
    support: 'Requires Permission',
    detail:
      'Uses standard Android Intents (PackageManager launcher query, ACTION_DIAL, ACTION_VIEW, google.navigation, AlarmClock.ACTION_SET_ALARM) with confirmation before sensitive actions.',
  },
  {
    feature: 'Silent Background Calls or Wi-Fi Toggle',
    support: 'Android Limited',
    detail:
      'Blocked by modern Android security policies for third-party apps. JARVIS opens the pre-filled dialer safely instead of placing silent calls.',
  },
];

export const RedmiSetupGuide: React.FC = () => {
  return (
    <div className="w-full space-y-8">
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-4 border-b border-slate-800/80 pb-5">
        <div>
          <h2 className="font-display text-2xl font-semibold text-slate-100">
            Build, Install &amp; Redmi A5 Configuration
          </h2>
          <p className="mt-1 text-sm text-slate-400">
            Instructions for compiling the APK, installing via USB or ADB, and configuring Redmi A5 battery policies for reliable background voice operation.
          </p>
        </div>
        <a
          href="/api/android/download-zip"
          className="inline-flex items-center justify-center gap-2 px-4 py-2.5 rounded-lg bg-cyan-400 text-slate-950 font-semibold text-sm hover:bg-cyan-300 transition-colors whitespace-nowrap shrink-0"
        >
          <Download className="w-4 h-4" />
          Download Android ZIP
        </a>
      </div>

      {/* Build & Install Steps */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
        <div className="rounded-xl border border-slate-800/80 bg-[#0B132B] p-5 space-y-3">
          <div className="flex items-center gap-2 text-cyan-400 text-sm font-semibold">
            <Smartphone className="w-4 h-4" />
            <span>01. Build with Android Studio</span>
          </div>
          <p className="text-sm text-slate-300 leading-relaxed">
            Extract the downloaded ZIP and open the root folder in Android Studio Ladybug or newer (JDK 17, SDK 35). Your live backend URL is pre-wired into <span className="font-mono text-xs text-cyan-300">app/build.gradle.kts</span> with zero API keys inside the APK.
          </p>
        </div>

        <div className="rounded-xl border border-slate-800/80 bg-[#0B132B] p-5 space-y-3">
          <div className="flex items-center gap-2 text-cyan-400 text-sm font-semibold">
            <Terminal className="w-4 h-4" />
            <span>02. Install APK via ADB</span>
          </div>
          <pre className="rounded-lg bg-[#030712] border border-slate-800 p-3 text-xs font-mono text-slate-200 overflow-x-auto">
{`gradle assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk`}
          </pre>
          <p className="text-xs text-slate-400">
            Or push to GitHub to run <span className="font-mono text-slate-300">.github/workflows/build-apk.yml</span> and download the compiled APK artifact directly onto your phone.
          </p>
        </div>

        <div className="rounded-xl border border-slate-800/80 bg-[#0B132B] p-5 space-y-3">
          <div className="flex items-center gap-2 text-cyan-400 text-sm font-semibold">
            <BatteryCharging className="w-4 h-4" />
            <span>03. Redmi A5 Battery Setup</span>
          </div>
          <ul className="space-y-2 text-sm text-slate-300">
            <li>1. Tap Setup inside JARVIS and grant Allow Unrestricted Battery.</li>
            <li>2. Open MIUI/HyperOS App Info, tap Battery saver, and choose No restrictions.</li>
            <li>3. Lock JARVIS in Recent Apps so 4GB RAM cleanup preserves the service.</li>
          </ul>
        </div>
      </div>

      {/* Honest Capability & Android Limit Matrix */}
      <div className="rounded-xl border border-slate-800/80 bg-[#0B132B] overflow-hidden">
        <div className="px-5 py-4 border-b border-slate-800/80">
          <h3 className="font-display text-lg font-semibold text-slate-100">
            Android Capability &amp; Platform Limit Matrix
          </h3>
          <p className="text-xs text-slate-400 mt-0.5">
            Transparent breakdown of what works natively, what requires user permissions, and where Android OS enforces boundaries.
          </p>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="border-b border-slate-800/80 text-xs text-slate-400">
                <th className="py-3 px-5 font-medium">Capability</th>
                <th className="py-3 px-5 font-medium">Status</th>
                <th className="py-3 px-5 font-medium">Technical Behavior on Redmi A5</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60 text-sm">
              {CAPABILITY_MATRIX.map((row) => (
                <tr key={row.feature} className="hover:bg-slate-800/20">
                  <td className="py-3.5 px-5 font-medium text-slate-100 whitespace-nowrap">
                    {row.feature}
                  </td>
                  <td className="py-3.5 px-5 whitespace-nowrap">
                    <span
                      className={`inline-flex items-center gap-1.5 text-xs font-medium ${
                        row.support === 'Full'
                          ? 'text-emerald-400'
                          : row.support === 'Requires Permission'
                            ? 'text-cyan-400'
                            : 'text-amber-400'
                      }`}
                    >
                      {row.support === 'Full' ? (
                        <CheckCircle2 className="w-3.5 h-3.5" />
                      ) : (
                        <ShieldAlert className="w-3.5 h-3.5" />
                      )}
                      {row.support}
                    </span>
                  </td>
                  <td className="py-3.5 px-5 text-slate-300 leading-relaxed">{row.detail}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
