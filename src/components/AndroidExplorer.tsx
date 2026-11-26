import React, { useEffect, useMemo, useState } from 'react';
import { Check, Copy, Download, FileCode2, FolderTree, Search } from 'lucide-react';

interface AndroidFileEntry {
  path: string;
  size: number;
}

export const AndroidExplorer: React.FC = () => {
  const [files, setFiles] = useState<AndroidFileEntry[]>([]);
  const [selectedPath, setSelectedPath] = useState<string>('');
  const [fileContent, setFileContent] = useState<string>('');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [loadingList, setLoadingList] = useState<boolean>(true);
  const [loadingFile, setLoadingFile] = useState<boolean>(false);
  const [copied, setCopied] = useState<boolean>(false);
  const [errorMsg, setErrorMsg] = useState<string>('');

  useEffect(() => {
    let active = true;
    setLoadingList(true);
    fetch('/api/android/files')
      .then((res) => {
        if (!res.ok) throw new Error('Failed to load Android project index.');
        return res.json();
      })
      .then((data: { files: AndroidFileEntry[] }) => {
        if (!active) return;
        setFiles(data.files);
        const defaultFile =
          data.files.find((f) => f.path.endsWith('JarvisVoiceService.kt')) || data.files[0];
        if (defaultFile) {
          setSelectedPath(defaultFile.path);
        }
        setLoadingList(false);
      })
      .catch((err: unknown) => {
        if (!active) return;
        setErrorMsg(err instanceof Error ? err.message : 'Unable to load files.');
        setLoadingList(false);
      });

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!selectedPath) return;
    let active = true;
    setLoadingFile(true);
    fetch(`/api/android/file?path=${encodeURIComponent(selectedPath)}`)
      .then((res) => {
        if (!res.ok) throw new Error('Unable to read selected file.');
        return res.json();
      })
      .then((data: { content: string }) => {
        if (!active) return;
        setFileContent(data.content);
        setLoadingFile(false);
      })
      .catch((err: unknown) => {
        if (!active) return;
        setFileContent('');
        setErrorMsg(err instanceof Error ? err.message : 'Unable to read file.');
        setLoadingFile(false);
      });

    return () => {
      active = false;
    };
  }, [selectedPath]);

  const filteredFiles = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return files;
    return files.filter((f) => f.path.toLowerCase().includes(q));
  }, [files, searchQuery]);

  const handleCopy = async () => {
    if (!fileContent) return;
    await navigator.clipboard.writeText(fileContent);
    setCopied(true);
    setTimeout(() => setCopied(false), 1800);
  };

  return (
    <div className="w-full space-y-6">
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-4 border-b border-slate-800/80 pb-5">
        <div>
          <h2 className="font-display text-2xl font-semibold text-slate-100">
            Android Kotlin &amp; Jetpack Compose Project
          </h2>
          <p className="mt-1 text-sm text-slate-400">
            Complete source tree targeting Redmi A5 (minSdk 26, targetSdk 35) with Foreground Microphone Service, SpeechRecognizer, TextToSpeech, and EncryptedSharedPreferences.
          </p>
        </div>
        <a
          href="/api/android/download-zip"
          className="inline-flex items-center justify-center gap-2 px-4 py-2.5 rounded-lg bg-cyan-400 text-slate-950 font-semibold text-sm hover:bg-cyan-300 transition-colors whitespace-nowrap shrink-0"
        >
          <Download className="w-4 h-4" />
          Download Project ZIP
        </a>
      </div>

      {errorMsg && (
        <div className="rounded-lg border border-red-500/40 bg-red-950/30 px-4 py-3 text-sm text-red-200">
          {errorMsg}
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-12 border border-slate-800/80 rounded-xl bg-[#0B132B] overflow-hidden min-h-[560px]">
        <aside className="lg:col-span-4 border-b lg:border-b-0 lg:border-r border-slate-800/80 flex flex-col">
          <div className="p-3 border-b border-slate-800/80">
            <div className="relative">
              <Search className="w-4 h-4 text-slate-500 absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Filter Android files..."
                className="w-full bg-[#030712] border border-slate-800 rounded-lg pl-9 pr-3 py-2 text-xs text-slate-100 placeholder:text-slate-500 focus:outline-none focus:border-cyan-500"
              />
            </div>
            <div className="mt-2 flex items-center justify-between text-xs text-slate-400 px-1 tabular-nums">
              <span className="inline-flex items-center gap-1.5">
                <FolderTree className="w-3.5 h-3.5 text-cyan-400" />
                android/
              </span>
              <span>{filteredFiles.length} files</span>
            </div>
          </div>

          <div className="flex-1 overflow-y-auto max-h-[520px] divide-y divide-slate-800/40">
            {loadingList ? (
              <div className="p-4 text-xs text-slate-400">Loading project tree...</div>
            ) : filteredFiles.length === 0 ? (
              <div className="p-4 text-xs text-slate-400">No files match your filter.</div>
            ) : (
              filteredFiles.map((file) => {
                const active = file.path === selectedPath;
                return (
                  <button
                    key={file.path}
                    type="button"
                    onClick={() => setSelectedPath(file.path)}
                    className={`w-full text-left px-3.5 py-2.5 flex items-center justify-between gap-2 text-xs transition-colors ${
                      active
                        ? 'bg-cyan-500/15 text-cyan-300'
                        : 'text-slate-300 hover:bg-slate-800/40'
                    }`}
                  >
                    <span className="truncate font-mono">{file.path}</span>
                    <span className="text-[11px] text-slate-500 tabular-nums shrink-0">
                      {(file.size / 1024).toFixed(1)} KB
                    </span>
                  </button>
                );
              })
            )}
          </div>
        </aside>

        <section className="lg:col-span-8 flex flex-col bg-[#030712]">
          <div className="flex items-center justify-between gap-4 px-4 py-3 border-b border-slate-800/80 bg-[#0B132B]/60">
            <div className="flex items-center gap-2 min-w-0">
              <FileCode2 className="w-4 h-4 text-cyan-400 shrink-0" />
              <span className="font-mono text-xs text-slate-200 truncate">
                {selectedPath || 'Select a file'}
              </span>
            </div>
            <button
              type="button"
              onClick={handleCopy}
              disabled={!fileContent}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md border border-slate-700 text-xs font-medium text-slate-200 hover:bg-slate-800 disabled:opacity-40 transition-colors whitespace-nowrap shrink-0"
            >
              {copied ? <Check className="w-3.5 h-3.5 text-cyan-400" /> : <Copy className="w-3.5 h-3.5" />}
              {copied ? 'Copied' : 'Copy'}
            </button>
          </div>

          <div className="flex-1 overflow-auto max-h-[520px] p-4">
            {loadingFile ? (
              <div className="text-xs font-mono text-slate-400">Reading source file...</div>
            ) : (
              <pre className="text-xs leading-relaxed font-mono text-slate-200 whitespace-pre">
                <code>{fileContent}</code>
              </pre>
            )}
          </div>
        </section>
      </div>
    </div>
  );
};
