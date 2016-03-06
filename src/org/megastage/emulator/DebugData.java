package org.megastage.emulator;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DebugData {
    public HashMap<String, String> defines = new HashMap<>();
    public HashMap<String, String> labels = new HashMap<>();
    public HashMap<String, String> constants = new HashMap<>();
    public List<LineData> lines = new ArrayList<>(65536);
    public int[] memToLineNum = new int[65536];
    public String[] memToLabel = new String[65536];

    private int curAddr = 0;

    public static DebugData load(String filename) throws IOException {
        DebugData me = new DebugData();

        BufferedReader br = new BufferedReader(new FileReader(filename));
        me.init(br);
        br.close();

        return me;
    }

    private void init(BufferedReader br) throws IOException {
        defines = initMap(br);
        labels = initMap(br);

        constants.putAll(labels);
        constants.putAll(defines);

        for(String label: labels.keySet()) {
            String value = labels.get(label);
            memToLabel[Integer.parseInt(value)] = label;
        }

        String label = memToLabel[0] == null ? "MEM_START": memToLabel[0];
        int base = 0;
        for(int i=1; i < memToLabel.length; i++) {
            if(memToLabel[i] == null) {
                if(i - base < 32) {
                    memToLabel[i] = label + " " + (i - base);
                } else {
                    memToLabel[i] = "";
                }
            } else {
                label = memToLabel[i];
                base = i;
            }

        }

        String filename = br.readLine();
        while(filename != null) {
            LineData ld = new LineData(filename, br);
            for(int addr: ld.mem) {
                memToLineNum[addr] = lines.size();
            }
            lines.add(ld);
            filename = br.readLine();
        }
    }

    private HashMap<String, String> initMap(BufferedReader br) throws IOException {
        int numItems = Integer.parseInt(br.readLine());

        HashMap<String, String> map = new HashMap<>();
        for(int i=0; i < numItems; i++) {
            String key = br.readLine();
            String val = br.readLine();
            map.put(key, val);
        }
        return map;
    }

    public class LineData {
        public String filename;
        public int lineNum;
        public String text;
        public List<Integer> mem;

        public LineData(String filename, BufferedReader br) throws IOException {
            this.filename = filename;
            lineNum = Integer.parseInt(br.readLine()) + 1;

            int len = Integer.parseInt(br.readLine());

            mem = new ArrayList<>(len);
            for(int i=0; i < len; i++) {
                int addr = Integer.parseInt(br.readLine(), 16);
                if(addr == curAddr) {
                    curAddr++;
                    mem.add(addr);
                }
                br.readLine();
            }

            text = br.readLine();
        }
    }
}
