/*
 * Copyright (c) 2004-2016 Laboratório de Sistemas e Tecnologia Subaquática and Authors
 * All rights reserved.
 * Faculdade de Engenharia da Universidade do Porto
 * Departamento de Engenharia Electrotécnica e de Computadores
 * Rua Dr. Roberto Frias s/n, 4200-465 Porto, Portugal
 *
 * For more information please see <http://whale.fe.up.pt/neptus>.
 * 
 * Created by Hugo Dias
 * Nov 17, 2011
 */
package pt.lsts.s57.resources.entities.s52.symbol;

import java.awt.image.BufferedImage;
import java.io.File;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;

import pt.lsts.s57.mc.MarinerControls;
import pt.lsts.s57.resources.entities.s52.ColorTable;

/**
 * @author Hugo Dias
 * @author Paulo Dias
 */

public class SymbolTest extends JFrame {

    /**
     * 
     */
    private static final long serialVersionUID = -4686118902844480321L;
    Configuration config;

    @SuppressWarnings("serial")
    SymbolTest() throws ConfigurationException, Exception {
        config = new PropertiesConfiguration("resources/config.properties");
        config.setProperty("s52.resources",
                new File("resources", config.getString("s52.resources")).getCanonicalPath());
        ColorTable colorTable = ColorTable.forge(config);
        Symbols symbols = Symbols.forge(config, colorTable);
        MarinerControls mc = MarinerControls.forge();

//        S52Pattern symbol = symbols.getPattern("MARSHES1");
        S52Symbol symbol = symbols.getSymbol("SAFCON01");
//        symbol = symbols.getSymbol("RFNERY01");
//        symbol = symbols.getSymbol("BCNDEF13");
//        symbol = symbols.getSymbol("NORTHAR1");
//        symbol = symbols.getSymbol("SCALEB11");
//        symbol = symbols.getSymbol("SCALEB10");
        System.out.println("w" + symbol.width + "::h" + symbol.height
                + "::bx" + symbol.hotspotX+ "::by" + symbol.hotspotY
                + "::ox" + symbol.offsetX+ "::oy" + symbol.offsetY);
        System.out.println(symbol.symbolType + " :: " + symbol.symbolData);
//        symbol.width = 50;
//        symbol.height = 50;
        final BufferedImage image = symbol.getScaled(null, mc, 0, 10);

        JLabel picLabel = new JLabel(new ImageIcon(image)) {
//            @Override
//            public void paint(Graphics g) {
//                super.paint(g);
//                Graphics2D g2 = (Graphics2D) g.create();
////                g2.scale(20, 20);
//                g2.drawImage(image, 0, 0, image.getWidth(), image.getHeight(), null);
//                g2.dispose();
//            }
        };
        add(picLabel);

        this.setSize(1000, 1000);
        // pack();
        setVisible(true);
    }

    public static void main(String args[]) {
        try {
            new SymbolTest();
        }
        catch (ConfigurationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
