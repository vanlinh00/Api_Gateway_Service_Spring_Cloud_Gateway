import React, { useState } from 'react';
import { 
  BookOpen, 
  Lightbulb, 
  Copy, 
  Check, 
  ShieldAlert, 
  Cpu, 
  Zap, 
  Terminal, 
  CheckCircle2,
  Database
} from 'lucide-react';

export const GuideSection: React.FC = () => {
  const [copiedKey, setCopiedKey] = useState<string | null>(null);

  const copyText = (key: string, text: string) => {
    navigator.clipboard.writeText(text);
    setCopiedKey(key);
    setTimeout(() => setCopiedKey(null), 2000);
  };

  const curlMissing = `curl -i -X GET http://localhost:8080/api/v1/users/profile`;

  const curlRevoke = `redis-cli SET "jwt:blacklist:jti-revoked-001" "REVOKED" EX 3600`;

  const curlBlacklisted = `curl -i -X GET http://localhost:8080/api/v1/users/profile \\
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhbGljZSIsImp0aSI6Imp0aS1yZXZva2VkLTAwMSJ9.signature"`;

  const curlValid = `curl -i -X GET http://localhost:8080/api/v1/users/profile \\
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhbGljZSIsImp0aSI6Imp0aS1hY3RpdmUtODg4In0.signature"`;

  return (
    <div className="space-y-6 text-slate-200">
      {/* 3 Core Architecture Insights */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5">
          <div className="p-2.5 rounded-xl bg-purple-950/80 border border-purple-800/60 text-purple-400 w-fit mb-3">
            <Cpu className="w-5 h-5" />
          </div>
          <h4 className="font-bold text-sm text-slate-100 mb-1">100% Non-blocking Reactive</h4>
          <p className="text-xs text-slate-400 leading-relaxed">
            Spring Cloud Gateway chạy trên Reactor Netty. Nếu dùng <code className="text-red-400">RedisTemplate</code> thông thường sẽ gây block luồng Event Loop, làm sập toàn bộ Gateway khi có tải cao. Bắt buộc phải dùng <code className="text-emerald-400">ReactiveStringRedisTemplate</code>.
          </p>
        </div>

        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5">
          <div className="p-2.5 rounded-xl bg-amber-950/80 border border-amber-800/60 text-amber-400 w-fit mb-3">
            <Database className="w-5 h-5" />
          </div>
          <h4 className="font-bold text-sm text-slate-100 mb-1">Chiến Lược Key: JTI + TTL</h4>
          <p className="text-xs text-slate-400 leading-relaxed">
            Chỉ lưu định danh <code className="text-amber-400">jti</code> (claim JWT ID, độ dài ~36 ký tự) vào Redis thay vì lưu cả chuỗi JWT dài 500+ ký tự. Đặt TTL chính xác bằng thời gian còn lại của token để Redis tự giải phóng RAM.
          </p>
        </div>

        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5">
          <div className="p-2.5 rounded-xl bg-emerald-950/80 border border-emerald-800/60 text-emerald-400 w-fit mb-3">
            <Zap className="w-5 h-5" />
          </div>
          <h4 className="font-bold text-sm text-slate-100 mb-1">Tốc Độ Tra Cứu O(1)</h4>
          <p className="text-xs text-slate-400 leading-relaxed">
            Lệnh <code className="text-emerald-400">hasKey()</code> trong Redis có độ phức tạp thời gian <code className="text-emerald-400">O(1)</code>, độ trễ chỉ <strong>&lt; 1ms</strong> trên mạng nội bộ cụm microservice, hầu như không ảnh hưởng đến throughput của Gateway.
          </p>
        </div>
      </div>

      {/* Copyable Test Commands */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl">
        <h3 className="font-bold text-sm text-slate-100 uppercase tracking-wider mb-4 flex items-center gap-2">
          <Terminal className="w-4 h-4 text-emerald-400" />
          Các Lệnh cURL & Redis-CLI Kiểm Thử Thực Tế
        </h3>

        <div className="space-y-4">
          {/* Item 1 */}
          <div className="bg-slate-950 border border-slate-800 rounded-xl p-4">
            <div className="flex items-center justify-between mb-2">
              <div className="flex items-center gap-2">
                <span className="text-xs font-bold text-red-400">Trường hợp 1:</span>
                <span className="text-xs text-slate-300">Không truyền Header Authorization (Mong đợi: HTTP 401)</span>
              </div>
              <button
                onClick={() => copyText('missing', curlMissing)}
                className="text-xs text-slate-400 hover:text-white flex items-center gap-1 cursor-pointer"
              >
                {copiedKey === 'missing' ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                {copiedKey === 'missing' ? 'Đã sao chép' : 'Sao chép'}
              </button>
            </div>
            <pre className="font-mono text-xs text-slate-300 bg-slate-900 p-2.5 rounded-lg overflow-x-auto">
              {curlMissing}
            </pre>
          </div>

          {/* Item 2 */}
          <div className="bg-slate-950 border border-slate-800 rounded-xl p-4">
            <div className="flex items-center justify-between mb-2">
              <div className="flex items-center gap-2">
                <span className="text-xs font-bold text-amber-400">Trường hợp 2:</span>
                <span className="text-xs text-slate-300">Đưa Token vào Redis Blacklist (Mô phỏng user logout với TTL 1 giờ)</span>
              </div>
              <button
                onClick={() => copyText('revoke', curlRevoke)}
                className="text-xs text-slate-400 hover:text-white flex items-center gap-1 cursor-pointer"
              >
                {copiedKey === 'revoke' ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                {copiedKey === 'revoke' ? 'Đã sao chép' : 'Sao chép'}
              </button>
            </div>
            <pre className="font-mono text-xs text-slate-300 bg-slate-900 p-2.5 rounded-lg overflow-x-auto">
              {curlRevoke}
            </pre>
          </div>

          {/* Item 3 */}
          <div className="bg-slate-950 border border-slate-800 rounded-xl p-4">
            <div className="flex items-center justify-between mb-2">
              <div className="flex items-center gap-2">
                <span className="text-xs font-bold text-red-400">Trường hợp 3:</span>
                <span className="text-xs text-slate-300">Gửi request với Token đã bị thu hồi (Mong đợi: HTTP 401 + Log WARN)</span>
              </div>
              <button
                onClick={() => copyText('blacklisted', curlBlacklisted)}
                className="text-xs text-slate-400 hover:text-white flex items-center gap-1 cursor-pointer"
              >
                {copiedKey === 'blacklisted' ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                {copiedKey === 'blacklisted' ? 'Đã sao chép' : 'Sao chép'}
              </button>
            </div>
            <pre className="font-mono text-xs text-slate-300 bg-slate-900 p-2.5 rounded-lg overflow-x-auto">
              {curlBlacklisted}
            </pre>
          </div>

          {/* Item 4 */}
          <div className="bg-slate-950 border border-slate-800 rounded-xl p-4">
            <div className="flex items-center justify-between mb-2">
              <div className="flex items-center gap-2">
                <span className="text-xs font-bold text-emerald-400">Trường hợp 4:</span>
                <span className="text-xs text-slate-300">Gửi request với Token hợp lệ (Mong đợi: HTTP 200 Forwarded)</span>
              </div>
              <button
                onClick={() => copyText('valid', curlValid)}
                className="text-xs text-slate-400 hover:text-white flex items-center gap-1 cursor-pointer"
              >
                {copiedKey === 'valid' ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                {copiedKey === 'valid' ? 'Đã sao chép' : 'Sao chép'}
              </button>
            </div>
            <pre className="font-mono text-xs text-slate-300 bg-slate-900 p-2.5 rounded-lg overflow-x-auto">
              {curlValid}
            </pre>
          </div>
        </div>
      </div>
    </div>
  );
};
