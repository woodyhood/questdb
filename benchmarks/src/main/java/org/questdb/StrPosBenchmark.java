/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2022 QuestDB
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

import io.questdb.cairo.sql.Function;
import io.questdb.cairo.sql.Record;
import io.questdb.griffin.engine.functions.StrFunction;
import io.questdb.griffin.engine.functions.constants.CharConstant;
import io.questdb.griffin.engine.functions.str.StrPosCharFunctionFactory;
import io.questdb.griffin.engine.functions.str.StrPosFunctionFactory;
import io.questdb.std.Rnd;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class StrPosBenchmark {

    private static final int N = 1_000_000;

    private final Record[] records;
    private final String[] strings;
    private final Function strposStrFunc;
    private final Function strposCharFunc;
    private final Rnd rnd = new Rnd();

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(StrPosBenchmark.class.getSimpleName())
                .addProfiler(GCProfiler.class)
                .warmupIterations(2)
                .measurementIterations(3)
                .forks(1)
                .build();

        new Runner(opt).run();
    }

    public StrPosBenchmark() {
        strings = new String[N];
        for (int i = 0; i < N; i++) {
            int startLen = rnd.nextInt(1000);
            strings[i] = "a".repeat(startLen) + ",b";
        }

        records = new Record[N];
        for (int i = 0; i < N; i++) {
            int finalI = i;
            records[i] = new Record() {
                @Override
                public String getStr(int col) {
                    return strings[finalI];
                }
            };
        }

        Function strFunc = new StrFunction() {
            @Override
            public CharSequence getStr(Record rec) {
                return rec.getStr(0);
            }

            @Override
            public CharSequence getStrB(Record rec) {
                return rec.getStr(0);
            }
        };
        Function substrFunc = new CharConstant(',');
        strposStrFunc = new StrPosFunctionFactory.Func(strFunc, substrFunc);
        strposCharFunc = new StrPosCharFunctionFactory.Func(strFunc, substrFunc);
    }

    @Benchmark
    public int testBaseline() {
        return rnd.nextInt(N);
    }

    @Benchmark
    public int testStrOverload() {
        int i = rnd.nextInt(N);
        return strposStrFunc.getInt(records[i]);
    }

    @Benchmark
    public int testCharOverload() {
        int i = rnd.nextInt(N);
        return strposCharFunc.getInt(records[i]);
    }
}
