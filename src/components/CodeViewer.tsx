import React, { useState } from 'react';
import { PROJECT_FILES } from '../data/sourceCode';
import { ProjectFile } from '../types';
import { 
  FileCode, 
  Copy, 
  Check, 
  Download, 
  FolderTree, 
  FileText, 
  Settings, 
  Cpu, 
  Terminal,
  ExternalLink,
  Search
} from 'lucide-react';
import JSZip from 'jszip';

export const CodeViewer: React.FC = () => {
  const [selectedFile, setSelectedFile] = useState<ProjectFile>(PROJECT_FILES[0]);
  const [copied, setCopied] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [isZipping, setIsZipping] = useState(false);

  const filteredFiles = PROJECT_FILES.filter(f => 
    f.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
    f.path.toLowerCase().includes(searchQuery.toLowerCase()) ||
    f.description.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const handleCopy = () => {
    navigator.clipboard.writeText(selectedFile.content);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleDownloadZip = async () => {
    setIsZipping(true);
    try {
      const zip = new JSZip();

      // Add each file to the zip maintaining the relative path inside api-gateway-service
      PROJECT_FILES.forEach((file) => {
        // file.path starts with "api-gateway-service/"
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
      console.error('Error creating zip:', err);
    } finally {
      setIsZipping(false);
    }
  };

  const getFileIcon = (category: ProjectFile['category'], name: string) => {
    if (name.endsWith('.yml') || name.endsWith('.yaml') || name.endsWith('.xml')) {
      return <Settings className="w-4 h-4 text-amber-400" />;
    }
    if (name.includes('Filter')) {
      return <Cpu className="w-4 h-4 text-emerald-400" />;
    }
    if (name.includes('Test')) {
      return <Terminal className="w-4 h-4 text-purple-400" />;
    }
    if (name.endsWith('.md')) {
      return <FileText className="w-4 h-4 text-blue-400" />;
    }
    return <FileCode className="w-4 h-4 text-cyan-400" />;
  };

  return (
    <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-2xl">
      {/* Top Header */}
      <div className="p-4 md:p-5 bg-slate-950 border-b border-slate-800 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="p-2.5 rounded-xl bg-emerald-950/80 border border-emerald-800/60 text-emerald-400">
            <FolderTree className="w-5 h-5" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h3 className="font-bold text-slate-100 text-base">Thư Mục Mã Nguồn Dự Án</h3>
              <span className="text-[11px] font-mono px-2 py-0.5 rounded bg-slate-800 text-emerald-400 border border-slate-700">
                /api-gateway-service
              </span>
            </div>
            <p className="text-xs text-slate-400">Spring Boot 3.4.3 • Spring Cloud 2024.0.0 • Java 17 • Redis Reactive</p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={handleDownloadZip}
            disabled={isZipping}
            className="flex items-center gap-2 px-3.5 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 text-white text-xs font-semibold shadow-lg shadow-emerald-950/50 transition cursor-pointer"
          >
            <Download className="w-3.5 h-3.5" />
            {isZipping ? 'Đang nén ZIP...' : 'Tải Về Toàn Bộ (.ZIP)'}
          </button>
        </div>
      </div>

      {/* Main Layout: File list sidebar + Code viewer */}
      <div className="grid grid-cols-1 lg:grid-cols-12 min-h-[560px]">
        {/* Sidebar */}
        <div className="lg:col-span-4 border-r border-slate-800 bg-slate-950/70 p-3 flex flex-col">
          {/* Search box */}
          <div className="relative mb-3">
            <Search className="w-3.5 h-3.5 absolute left-3 top-2.5 text-slate-500" />
            <input
              type="text"
              placeholder="Tìm file theo tên hoặc nội dung..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-8 pr-3 py-1.5 bg-slate-900 border border-slate-800 rounded-lg text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-emerald-500"
            />
          </div>

          <div className="text-[11px] font-semibold uppercase tracking-wider text-slate-500 px-2 py-1">
            Danh sách file ({filteredFiles.length})
          </div>

          <div className="flex-1 space-y-1 overflow-y-auto max-h-[500px] pr-1">
            {filteredFiles.map((file) => {
              const isSelected = selectedFile.path === file.path;
              return (
                <button
                  key={file.path}
                  onClick={() => setSelectedFile(file)}
                  className={`w-full text-left px-3 py-2 rounded-xl text-xs transition flex items-start gap-2.5 cursor-pointer ${
                    isSelected
                      ? 'bg-slate-800 text-white font-medium border border-slate-700 shadow-sm'
                      : 'text-slate-400 hover:bg-slate-900 hover:text-slate-200'
                  }`}
                >
                  <span className="mt-0.5 shrink-0">{getFileIcon(file.category, file.name)}</span>
                  <div className="flex-1 min-w-0">
                    <div className="truncate font-mono text-[11px] font-medium">{file.name}</div>
                    <div className="truncate text-[10px] text-slate-500 mt-0.5">{file.description}</div>
                  </div>
                </button>
              );
            })}
          </div>
        </div>

        {/* Code Content View */}
        <div className="lg:col-span-8 flex flex-col bg-slate-950">
          {/* File bar */}
          <div className="px-4 py-3 bg-slate-900/90 border-b border-slate-800 flex items-center justify-between">
            <div className="flex items-center gap-2 min-w-0">
              {getFileIcon(selectedFile.category, selectedFile.name)}
              <span className="font-mono text-xs text-slate-300 font-semibold truncate">
                {selectedFile.path}
              </span>
            </div>
            <button
              onClick={handleCopy}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-medium transition cursor-pointer border border-slate-700"
            >
              {copied ? (
                <>
                  <Check className="w-3.5 h-3.5 text-emerald-400" />
                  <span className="text-emerald-400">Đã chép!</span>
                </>
              ) : (
                <>
                  <Copy className="w-3.5 h-3.5 text-slate-400" />
                  <span>Sao chép mã</span>
                </>
              )}
            </button>
          </div>

          {/* Description banner */}
          <div className="px-4 py-2 bg-slate-900/40 border-b border-slate-800/80 text-xs text-slate-400 flex items-center gap-2">
            <span className="text-emerald-400 font-semibold shrink-0">Chức năng:</span>
            <span>{selectedFile.description}</span>
          </div>

          {/* Code text block with line numbers */}
          <div className="flex-1 overflow-x-auto p-4 font-mono text-xs leading-relaxed text-slate-300 bg-slate-950 selection:bg-emerald-900 selection:text-white max-h-[500px] overflow-y-auto">
            <pre className="table w-full">
              {selectedFile.content.split('\n').map((line, idx) => (
                <div key={idx} className="table-row hover:bg-slate-900/70">
                  <span className="table-cell text-right pr-4 select-none text-slate-600 text-[11px] w-10">
                    {idx + 1}
                  </span>
                  <span className="table-cell whitespace-pre">{line}</span>
                </div>
              ))}
            </pre>
          </div>
        </div>
      </div>
    </div>
  );
};
