package uk.co.sexeys;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.ImageObserver;
import java.sql.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.lang.Math;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class MBTile {
    int zoom = -1;
    List<Tile> tiles = new ArrayList<>();

    public class Tile{
        BufferedImage img;
        double[] bbox;
    }

    public ArrayList<Tile> GetChartFiles(double top, double left,
                                        double bottom, double right,
                                        int zoom) throws Exception {
        ArrayList<Tile> list = new ArrayList<>();
        int[] tl = LatLonToTile(top,left,zoom);
        int[] br = LatLonToTile(bottom,right,zoom);
        for ( TileDB tileDB : tileDBs) {
            if (tileDB.left > right) continue;
            if (tileDB.right < left) continue;
            if (tileDB.top < bottom) continue;
            if (tileDB.bottom > top) continue;

            for (int x = tl[0] - 1; x < br[0] + 1; x++) {
                for (int y = br[1] - 1; y < tl[1] + 1; y++) {
                    tileDB.getTile.setInt(1, zoom);
                    tileDB.getTile.setInt(2, x);
                    tileDB.getTile.setInt(3, y);

                    ResultSet rs = tileDB.getTile.executeQuery();
                    while (rs.next()) {

                        byte[] tileData = rs.getBytes("tile_data");
                        //                    int z = rs.getInt("zoom_level");
                        //                    int x = rs.getInt("tile_column");
                        //                    int y = rs.getInt("tile_row");
                        Tile t = new Tile();

                        t.bbox = tileBoundsLatLon(zoom, x, y);
                        //                    System.out.printf(
                        //                            "BBox: [%.6f, %.6f, %.6f, %.6f]%n",
                        //                            t.bbox[0], t.bbox[1], t.bbox[2], t.bbox[3]
                        //                    );

                        t.img = ImageIO.read(new ByteArrayInputStream(tileData));
                        list.add(t);

                    }

                }
            }
        }

        return list;
    }

    // ---- Bounding box calculation (MBTiles / TMS) ----
    // returns: minLon, minLat, maxLon, maxLat
    public static double[] tileBoundsLatLon(int z, int x, int yTms) {

        int n = 1 << z;

        double lonMin = (double) x / n * 360.0 - 180.0;
        double lonMax = (double) (x + 1) / n * 360.0 - 180.0;

        double latMax = tileLat(z, yTms, n);
        double latMin = tileLat(z, yTms - 1, n);

        return new double[]{lonMin, latMin, lonMax, latMax};
    }

    public static int[] LatLonToTile(double lat, double lng, int z) {
        int n = 1 << z;
        int x =  (int) ((lng +180)/360.0 * n);
        double rad = Math.toRadians(lat);
        double y = (1.0 - asinh(Math.tan(rad))/ Math.PI)/2.0 * n;
        int yTms = (1 << z) - 1 - (int) y;
        return new int[]{x,yTms};
    }

    public static double asinh(double x) {
        return Math.log(x + Math.sqrt(x * x + 1));
    }

    private static double tileLat(int z, int yTms, int n) {
        // TMS → XYZ conversion
        int y = (1 << z) - 1 - yTms;

        double rad = Math.atan(
                Math.sinh(Math.PI * (1.0 - 2.0 * y / n))
        );

        return Math.toDegrees(rad);
    }

    public void update(Mercator screen){
        final double maxTiles = 5; // found by trial and error
        if (zoom < 0) return; // Chart is off
        double dLon = screen.lon2-screen.lon1;
        double maxZoom = Math.log(maxTiles/ (dLon / 360)) /Math.log(2) ;
        if (zoom > maxZoom) zoom = (int) maxZoom;
        try {
            tiles = GetChartFiles(screen.lat1, screen.lon1, screen.lat2, screen.lon2, zoom);
        } catch (Exception ex) {
            System.out.println("Could not read CIBs");
        }
    }

    public void draw(Graphics g, Mercator screen, ImageObserver io) {
        if (zoom < 0) return; // Chart is off
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (Tile inst : tiles) {
            Vector2 leftBottom = screen.fromLatLngToPoint(inst.bbox[1], inst.bbox[0]);
            Vector2 rightTop = screen.fromLatLngToPoint(inst.bbox[3], inst.bbox[2]);

            int left = (int) leftBottom.x;
            int bottom = (int) leftBottom.y;
            int right = (int) rightTop.x;
            int top = (int) rightTop.y;

            if (left > screen.x2[0]) continue;
            if (right < 0) continue;
            if (bottom < 0) continue;
            if (top > screen.y2[0]) continue;

            double f_x = (double) (right - left) / inst.img.getWidth();
            double f_y = (double) (top - bottom) / inst.img.getHeight();

            AffineTransform trans = new AffineTransform();
            trans.translate(left, top);
            trans.scale(f_x, -f_y);

            g2.drawImage(inst.img, trans, io);
        }

        g2.dispose();
    }

    static class TileDB {
        PreparedStatement getTile;
        float top,left,bottom,right;
    }
    List<TileDB> tileDBs = new LinkedList<>();

    MBTile () {
        File mbtilesPath = new File(Main.root + "Charts");

        File[] fileList = mbtilesPath.listFiles();
        if(fileList == null) return;
        try {
            for (File f :fileList) {
                String file = f.getAbsolutePath();
                int i = file.lastIndexOf('.');
                if (i <= 0) continue;
                String extension = file.substring(i+1);
                if (!extension.contains("mbtiles") ) continue;
                TileDB tileDB = new TileDB();
                Connection conn = DriverManager.getConnection("jdbc:sqlite:" + file);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT name, value FROM metadata");
                while (rs.next()) {
                    String name = rs.getString("name");
                    if (name.contains("bounds")) {
                        String value = rs.getString("value");
                        String[] fields = value.split(",");
                        tileDB.left = Float.parseFloat(fields[0]);
                        tileDB.bottom = Float.parseFloat(fields[1]);
                        tileDB.right = Float.parseFloat(fields[2]);
                        tileDB.top = Float.parseFloat(fields[3]);
                        break;
                    }
                }
                tileDB.getTile = conn.prepareStatement(
                        "SELECT tile_data, zoom_level, tile_column, tile_row " +
                                "FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?");
                tileDBs.add(tileDB);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
