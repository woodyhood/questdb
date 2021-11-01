/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2020 QuestDB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package org.questdb;

import io.questdb.cairo.CairoException;
import io.questdb.cutlass.line.LineTcpSender;
import io.questdb.network.Net;
import io.questdb.std.FilesFacade;
import io.questdb.std.FilesFacadeImpl;
import io.questdb.std.Rnd;
import io.questdb.std.str.Path;
import io.questdb.std.str.StringSink;

public class LineTCPSenderMainVarLenStrings {
    private static final StringSink sink = new StringSink();
    private static final char[] chars = new char[] {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', ' ', '"', '\\', '\n'};

    public static void main(String[] args) {
        final long count = 2_000_000_000L;
        String hostIPv4 = "127.0.0.1";
        int port = 9009;
        int bufferCapacity = 64;

        final Rnd rnd = new Rnd();
        long start = System.nanoTime();
        FilesFacade ff = new FilesFacadeImpl();
        try(Path path = new Path()) {
            long logFd = -1;
            if (args.length == 1) {
                path.put(args[0]).$();
                logFd = ff.openRW(path);
            }
            try (LineTcpSender sender = new LoggingLineTcpSender(Net.parseIPv4(hostIPv4), port, bufferCapacity, logFd, ff)) {
                for (int i = 0; i < count; i++) {
                    sender.metric("md_msgs");
                    sender
                        .field("ts_nsec", rnd.nextPositiveLong())
                        .field("pkt_size", rnd.nextPositiveInt())
                        .field("pcap_file", nextString(rnd.nextPositiveInt() % 64, rnd))
                        .field("raw_msg", nextString(rnd.nextPositiveInt() % 512, rnd))
                        .field("Length", rnd.nextInt())
                        .field("MsgSeqNum", i)
                        .field("MsgType", rnd.nextInt() % 1000)
                        .field("src_ip", rnd.nextString(rnd.nextPositiveInt() % 16))
                        .field("dst_ip", rnd.nextString(rnd.nextPositiveInt() % 16))
                        .field("src_port", rnd.nextInt() % 10000)
                        .field("dst_port", rnd.nextInt() % 10000)
                        .field("first_dir", rnd.nextBoolean())
                        .$(i * 10_000_000L);
                }
                sender.flush();
            } finally {
                if (logFd > 0) {
                    ff.close(logFd);
                }
            }
        }
        System.out.println("Actual rate: " + (count * 1_000_000_000L / (System.nanoTime() - start)));
    }

    private static CharSequence nextString(int len, Rnd rnd) {
        sink.clear();
        int arrLen = chars.length;
        for (int i = 0; i < len; i++) {
            sink.put(chars[rnd.nextPositiveInt() % arrLen]);
        }
        return sink;
    }

    private static class LoggingLineTcpSender extends LineTcpSender {
        private final long outFileFd;
        private final FilesFacade ff;
        private long fileOffset = 0;

        public LoggingLineTcpSender(int sendToIPv4Address, int sendToPort, int bufferCapacity, long outFileFd, FilesFacade ff) {
            super(sendToIPv4Address, sendToPort, bufferCapacity);
            this.outFileFd = outFileFd;
            this.ff = ff;
        }

        @Override
        protected void sendToSocket(long fd, long lo, long sockaddr, int len) {
            if (outFileFd > -1) {
                if (ff.write(outFileFd, lo, len, fileOffset) != len) {
                    throw CairoException.instance(ff.errno()).put("Cannot write to file");
                }
                fileOffset += len;
            }
            super.sendToSocket(fd, lo, sockaddr, len);
        }
    }
}
