package org.elasticsearch.plugins;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

/**
 * @Author luorenshu(626115221 @ qq.com)
 * @date 2018/11/6 下午1:33
 **/
public class Test {

    public static void main(String[] args){
        String vector = "-1.1171832,-0.3780405,0.3337526,0.15939993,0.13482684,-0.31201524,-0.25829744,0.0725092,0.50081295,0.01827795,-0.43903276,0.034389388,-0.79033047,0.13608126,0.10281673,0.35327142,-0.17831749,0.23848195,-0.033562217,-0.26840922,0.5938388,-0.18657734,0.83033156,-0.23918945,0.14713119,-0.31941086,0.007708749,0.7578405,-1.022855,-0.50170726,0.3520088,-0.20467958,0.17235652,-0.139589,-0.1631417,0.8353913,0.10219347,-0.388795,0.07647285,-0.2908355,-0.24339563,-0.15178286,-0.07478876,-0.11378531,-0.45412683,-0.30300075,-0.7114446,-0.20202103,-0.61964625,0.08439794,0.10052217,0.96028036,-0.18361042,-0.94362885,-0.3447363,-0.07802301,-0.2356229,-0.25437707,-0.24875523,-0.016738228,0.30123222,0.9300449,-0.16621675,0.037936486,-0.5070223,0.54617697,-0.6377112,0.6944816,-0.27483928,-0.27019218,0.049831714,0.6240245,0.3427551,0.45595884,-0.18883742,-0.23559922,0.2295076,0.053038727,-0.32245815,-0.0010048009,-0.1508324,-0.79106486,0.35822892,0.23496896,-0.84840465,0.03205013,0.24696952,0.5942428,0.18813421,-0.31138882,-0.3398951,-0.40265787,-0.04076755,0.0605929,0.29719654,-0.8960997,0.13318661,-0.9137943,0.16636375,0.43241948,0.43644714,0.6275584,0.16942447,0.6579864,-0.013733178,-0.058598302,0.18622275,1.0300895,0.19062556,0.99226594,-0.13707326,-0.40462485,-0.014861291,-0.4756285,0.19205087,0.058207225,0.7265166,-0.47054908,0.2964989,-0.75413096,-0.04979557,0.42084476,0.29113492,-0.1872921,0.102164395,0.04577875,0.8091988,-0.39580247,0.10982584,-0.44471112,0.45644343,-0.6756127,0.32954213,0.6726754,-0.38056678,0.76600194,-0.73512554,-0.10454669,0.56802756,-0.12399674,-0.6879508,0.45780754,-0.39279765,0.17274807,-0.8299904,0.40999433,0.42996117,0.48164815,0.5319984,-0.7305975";
        tokenSplit(vector);
        jdkSplit(vector);
        jdkPattern(vector);
        encode(vector);
    }

    public static void tokenSplit(String vector){
        long begin = System.nanoTime();

        StringTokenizer token=new StringTokenizer(vector,",");
        double[] doubles = new double[200];
        int i=0;
        while(token.hasMoreElements()){
            doubles[i] = Double.parseDouble(token.nextToken());
            i++;
        }

        System.out.println("tokenSplit cost: "+(System.nanoTime()-begin));

    }

    public static void jdkSplit(String vector){
        long begin = System.nanoTime();

        String[] values =vector.split("\\,");
        double[] doubles = new double[values.length];
        for (int i = 0; i < values.length; i++) {
//            System.out.print(values[i]+"  ");
            doubles[i] = Double.parseDouble(values[i]);
        }

        System.out.println("jdkSplit cost: "+(System.nanoTime()-begin));

    }

    public static void jdkPattern(String vector){
        long begin = System.nanoTime();

        Pattern pattern = Pattern.compile("\\,");
        String[] values =pattern.split(vector);
        double[] doubles = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            doubles[i] = Double.parseDouble(values[i]);
        }

        System.out.println("jdkPattern cost: "+(System.nanoTime()-begin));

    }

    public static void encode(String vector){
        Pattern pattern = Pattern.compile("\\,");
        String[] values =pattern.split(vector);
        byte[] source = new byte[values.length*8];
        for (int i = 0; i < values.length; i++) {
            double value = Double.valueOf(values[i]);
            byte[] tmp = getBytes(value);

            for (int j=0; j<8; j++){
                source[i*8+j] = tmp[j];
            }
        }

        String encoded = Base64.getEncoder().encodeToString(source);


        long begin = System.nanoTime();
        double[] doubles = new double[values.length];
        //Base64 Decoded
        byte[] decodeBytes = Base64.getDecoder().decode(encoded);

        byte[] copyValue = new byte[8];
        for (int i=0; i<decodeBytes.length/8; i++){
            for (int j=0; j<8; j++){
                copyValue[j] = decodeBytes[i*8+j];
            }

            doubles[i] = getDouble(copyValue);
        }

        System.out.println("decode cost : "+(System.nanoTime()-begin));

    }

    public static byte[] getBytes(String data, String charsetName) {
        Charset charset = Charset.forName(charsetName);
        return data.getBytes(charset);
    }

    public static long getLong(byte[] bytes) {
        return (0xffL & (long) bytes[0]) | (0xff00L & ((long) bytes[1] << 8)) | (0xff0000L & ((long) bytes[2] << 16))
                | (0xff000000L & ((long) bytes[3] << 24)) | (0xff00000000L & ((long) bytes[4] << 32))
                | (0xff0000000000L & ((long) bytes[5] << 40)) | (0xff000000000000L & ((long) bytes[6] << 48))
                | (0xff00000000000000L & ((long) bytes[7] << 56));
    }

    public static byte[] getBytes(double data) {
        long intBits = Double.doubleToLongBits(data);
        return getBytes(intBits);
    }

    public static double getDouble(byte[] bytes) {
        long l = getLong(bytes);
        return Double.longBitsToDouble(l);
    }

    public static byte[] getBytes(long data) {
        byte[] bytes = new byte[8];
        bytes[0] = (byte) (data & 0xff);
        bytes[1] = (byte) ((data >> 8) & 0xff);
        bytes[2] = (byte) ((data >> 16) & 0xff);
        bytes[3] = (byte) ((data >> 24) & 0xff);
        bytes[4] = (byte) ((data >> 32) & 0xff);
        bytes[5] = (byte) ((data >> 40) & 0xff);
        bytes[6] = (byte) ((data >> 48) & 0xff);
        bytes[7] = (byte) ((data >> 56) & 0xff);
        return bytes;
    }
}
