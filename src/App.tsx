import React, { useState } from 'react';
import { 
  Code2, 
  Terminal, 
  Network, 
  BookOpen, 
  ShieldCheck, 
  Download, 
  ExternalLink,
  Layers,
  Database,
  Cpu
} from 'lucide-react';
import { CodeViewer } from './components/CodeViewer';
import { Simulator } from './components/Simulator';
import { ArchitectureFlow } from './components/ArchitectureFlow';
import { GuideSection } from './components/GuideSection';
import { PROJECT_FILES } from './data/sourceCode';
import JSZip from 'jszip';

export default function App() {
  const [activeTab, setActiveTab] = useState<'code' | 'simulator' | 'flow' | 'guide'>('code');
  const [isDownloading, setIsDownloading] = useState(false);

  const handleDownloadAll = async () => {
    setIsDownloading(true);
    try {
      const zip = new JSZip();
      PROJECT_FILES.forEach(file => {
        zip.file(file.path, file.content);
      });
      const blob = await zip.generateAsync({ type: 'blob' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'api-gateway-service-spring-boot-3.4.zip';
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } catch (err) {
      console.error(err);
    } finally {
      setIsDownloading(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans selection:bg-emerald-900 selection:text-white">
      {/* Top Header */}
      <header className="border-b border-slate-800/80 bg-slate-950/80 backdrop-blur sticky top-0 z-30">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 py-3.5 flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div className="flex items-center gap-3.5">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-emerald-600 to-teal-800 flex items-center justify-center text-white shadow-lg shadow-emerald-950/60 shrink-0">
              <ShieldCheck className="w-6 h-6" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-base sm:text-lg font-bold text-white tracking-tight">
                  Spring Cloud Gateway • JWT Blacklist with Redis
                </h1>
                <span className="hidden sm:inline-block px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-950 text-emerald-400 border border-emerald-800/80">
                  Spring Boot 3.4
                </span>
              </div>
              <p className="text-xs text-slate-400">
                Mã nguồn hoàn chỉnh trong thư mục <code className="text-emerald-400 font-mono">/api-gateway-service</code> với Reactive GlobalFilter & Redis Blacklist
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <div className="hidden lg:flex items-center gap-1.5 text-[11px] text-slate-400 bg-slate-900 px-3 py-1.5 rounded-xl border border-slate-800">
              <span className="w-2 h-2 rounded-full bg-emerald-400"></span>
              <span>Java 17 • Spring Cloud 2024.0.0</span>
            </div>

            <button
              onClick={handleDownloadAll}
              disabled={isDownloading}
              className="flex items-center gap-2 px-3.5 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 text-white text-xs font-semibold shadow-lg shadow-emerald-950 transition cursor-pointer"
            >
              <Download className="w-4 h-4" />
              <span>{isDownloading ? 'Đang tạo ZIP...' : 'Tải Về Dự Án (.ZIP)'}</span>
            </button>
          </div>
        </div>

        {/* Tab Navigation */}
        <div className="max-w-7xl mx-auto px-4 sm:px-6 flex gap-2 border-t border-slate-900 overflow-x-auto py-1">
          <button
            onClick={() => setActiveTab('code')}
            className={`flex items-center gap-2 px-4 py-2.5 rounded-xl text-xs font-semibold transition cursor-pointer shrink-0 ${
              activeTab === 'code'
                ? 'bg-slate-900 text-emerald-400 border border-slate-800 shadow-sm'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/50'
            }`}
          >
            <Code2 className="w-4 h-4" />
            <span>Mã Nguồn Chi Tiết (Source Code)</span>
          </button>

          <button
            onClick={() => setActiveTab('simulator')}
            className={`flex items-center gap-2 px-4 py-2.5 rounded-xl text-xs font-semibold transition cursor-pointer shrink-0 ${
              activeTab === 'simulator'
                ? 'bg-slate-900 text-emerald-400 border border-slate-800 shadow-sm'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/50'
            }`}
          >
            <Terminal className="w-4 h-4" />
            <span>Giả Lập & Kiểm Thử (Live Sandbox)</span>
          </button>

          <button
            onClick={() => setActiveTab('flow')}
            className={`flex items-center gap-2 px-4 py-2.5 rounded-xl text-xs font-semibold transition cursor-pointer shrink-0 ${
              activeTab === 'flow'
                ? 'bg-slate-900 text-emerald-400 border border-slate-800 shadow-sm'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/50'
            }`}
          >
            <Network className="w-4 h-4" />
            <span>Sơ Đồ Luồng Reactive (Architecture)</span>
          </button>

          <button
            onClick={() => setActiveTab('guide')}
            className={`flex items-center gap-2 px-4 py-2.5 rounded-xl text-xs font-semibold transition cursor-pointer shrink-0 ${
              activeTab === 'guide'
                ? 'bg-slate-900 text-emerald-400 border border-slate-800 shadow-sm'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900/50'
            }`}
          >
            <BookOpen className="w-4 h-4" />
            <span>Hướng Dẫn & Lệnh cURL</span>
          </button>
        </div>
      </header>

      {/* Main Container */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 py-6">
        {activeTab === 'code' && <CodeViewer />}
        {activeTab === 'simulator' && <Simulator />}
        {activeTab === 'flow' && <ArchitectureFlow />}
        {activeTab === 'guide' && <GuideSection />}
      </main>

      {/* Footer */}
      <footer className="border-t border-slate-900 bg-slate-950/60 py-4 text-center text-xs text-slate-500">
        <div className="max-w-7xl mx-auto px-4 flex flex-col sm:flex-row items-center justify-between gap-2">
          <span>Spring Cloud Gateway 2024.0.0 • Spring Boot 3.4.3 • Spring Data Redis Reactive (Lettuce)</span>
          <span className="font-mono text-slate-600">Thư mục độc lập: /api-gateway-service</span>
        </div>
      </footer>
    </div>
  );
}
