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
    public List<LineData> lines = new ArrayList<>(65536);
    public int[] memToLineNum = new int[65536];

    public static DebugData load(String filename) throws IOException {
        DebugData me = new DebugData();

        BufferedReader br = new BufferedReader(new FileReader(filename));
        me.init(br);

        return me;
    }

    private void init(BufferedReader br) throws IOException {
        defines = initMap(br);
        labels = initMap(br);

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

    public static class LineData {
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
                mem.add(Integer.parseInt(br.readLine(), 16));
                br.readLine();
            }

            text = br.readLine();
        }
    }
}
