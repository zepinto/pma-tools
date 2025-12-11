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
 * 3 de Dez de 2013
 */
package pt.lsts.s57.ui;

import java.awt.BorderLayout;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import net.miginfocom.swing.MigLayout;
import pt.lsts.s57.mc.MarinerControls;
import pt.lsts.s57.resources.entities.s52.ColorScheme;
import pt.lsts.s57.resources.entities.s52.DisplayCategory;
import pt.lsts.s57.resources.entities.s52.LookupTableType;

/**
 * @author Hugo Dias
 */
public class MarinerControlsOptionsPanel extends JPanel implements ItemListener, OptionsPanel {

    private static final long serialVersionUID = 6304636551721739773L;

    private MarinerControls mc;
    private OptionsDialog parent;
    private JComboBox<String> pointStyle;
    private JTextField safety;
    private JTextField deep;
    private JTextField shallow;
    private JComboBox<String> areaStyle;
    private JComboBox<String> colorScheme;
    private JComboBox<String> displayCat;
    private JCheckBox scaleFilter;
    private JCheckBox dateFilter;
    private JCheckBox showText;
    private JCheckBox soundings;
    private JCheckBox otherText;
    private JCheckBox contourLabels;
    private JCheckBox lowAccurateSymbols;
    private JCheckBox showIsolatedDangerInShallowWater;
    
    public MarinerControlsOptionsPanel(final MarinerControls mc, final OptionsDialog parent) {
        super();
        this.mc = mc;
        this.parent = parent;
        this.setLayout(new BorderLayout(0, 0));

        JPanel options = new JPanel();
        options.setLayout(new MigLayout("", "[100px,left][100px,fill]",
                "[20px][20px][20px][20px][20px][20px][][20px][][23px][23px][23px][][23px][][]"));
        this.add(options, BorderLayout.CENTER);

        pointStyle = new JComboBox<String>();
        pointStyle.addItemListener(this);
        pointStyle.setModel(new DefaultComboBoxModel<String>(new String[] { "PAPER_CHART", "SIMPLIFIED" }));
        pointStyle.setSelectedItem(mc.getPointStyle().toString());
        options.add(pointStyle, "cell 1 0,alignx left");

        JLabel lblpointStyle = new JLabel("Point Style");
        lblpointStyle.setLabelFor(pointStyle);
        options.add(lblpointStyle, "cell 0 0,alignx left,aligny center");

        areaStyle = new JComboBox<String>();
        areaStyle.setModel(new DefaultComboBoxModel<String>(new String[] { LookupTableType.PLAIN_BOUNDARIES.toString(),
                LookupTableType.SYMBOLIZED_BOUNDARIES.toString() }));
        areaStyle.setSelectedItem(mc.getAreaStyle().toString());
        areaStyle.addItemListener(this);
        options.add(areaStyle, "cell 1 1");

        JLabel lblareaStyle = new JLabel("Area Style");
        lblareaStyle.setLabelFor(areaStyle);
        options.add(lblareaStyle, "cell 0 1,alignx left,aligny center");

        colorScheme = new JComboBox<String>();
        colorScheme.setModel(new DefaultComboBoxModel<String>(
                new String[] { "DUSK", "NIGHT", "DAY_BRIGHT", "DAY_BLACKBACK", "DAY_WHITEBACK" }));
        colorScheme.setSelectedItem(mc.getColorScheme().toString());
        colorScheme.addItemListener(this);
        options.add(colorScheme, "cell 1 2,growx,aligny center");

        JLabel lblColorScheme = new JLabel("Color Scheme");
        lblColorScheme.setLabelFor(colorScheme);
        options.add(lblColorScheme, "cell 0 2,alignx left,aligny center");

        displayCat = new JComboBox<String>();
        displayCat.setModel(new DefaultComboBoxModel<String>(
                new String[] { "DISPLAYBASE", "STANDARD", "OTHER", "MARINERS_OTHER", "MARINERS_STANDARD" }));
        displayCat.setSelectedItem(mc.getDisplayCategory().toString());
        displayCat.addItemListener(this);
        options.add(displayCat, "cell 1 3,growx,aligny center");

        JLabel lblDisplayCategory = new JLabel("Display Category");
        lblDisplayCategory.setLabelFor(displayCat);
        options.add(lblDisplayCategory, "cell 0 3,alignx left,aligny center");

        // text fields
        safety = new JTextField();
        safety.setText(Double.toString(mc.getSafetyContour()));
        safety.setColumns(5);
        safety.getDocument().addDocumentListener(new DocumentListener() {

            @Override
            public void removeUpdate(DocumentEvent e) {
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                double safety1 = Double.valueOf(safety.getText());
                mc.setSafetyContour(safety1);
                parent.refreshRenderer();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
            }
        });
        options.add(safety, "cell 1 5,alignx left,aligny center");

        deep = new JTextField();
        deep.setText(Double.toString(mc.getDeepContour()));
        deep.setColumns(5);
        deep.getDocument().addDocumentListener(new DocumentListener() {

            @Override
            public void removeUpdate(DocumentEvent e) {
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                double deep1 = Double.valueOf(deep.getText());
                mc.setDeepContour(deep1);
                parent.refreshRenderer();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
            }
        });
        options.add(deep, "cell 1 6,alignx left,aligny center");

        shallow = new JTextField();
        shallow.setText(Double.toString(mc.getShallowContour()));
        shallow.setColumns(5);
        shallow.getDocument().addDocumentListener(new DocumentListener() {

            @Override
            public void removeUpdate(DocumentEvent e) {
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                double shallow1 = Double.valueOf(shallow.getText());
                mc.setShallowContour(shallow1);
                parent.refreshRenderer();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
            }
        });
        options.add(shallow, "cell 1 4,alignx left,aligny center");

        JLabel lblShallow = new JLabel("Shallow (meters)");
        lblShallow.setLabelFor(shallow);
        options.add(lblShallow, "cell 0 4,alignx left,aligny center");

        JLabel lblSafety = new JLabel("Safety (meters)");
        lblSafety.setLabelFor(safety);
        options.add(lblSafety, "cell 0 5,alignx left,aligny center");

        JLabel lblDeep = new JLabel("Deep (meters)");
        lblDeep.setLabelFor(deep);
        options.add(lblDeep, "cell 0 6,alignx left,aligny center");

        // check boxs
        showText = new JCheckBox("Show Text");
        showText.setSelected(mc.isShowImportantText());
        showText.addItemListener(this);
        options.add(showText, "cell 0 7,alignx left,aligny center");

        otherText = new JCheckBox("Other Text");
        otherText.setSelected(mc.isShowOtherText());
        otherText.addItemListener(this);
        options.add(otherText, "cell 0 8,alignx left,aligny center");

        scaleFilter = new JCheckBox("Scale Filter");
        scaleFilter.setSelected(mc.isScaleFilter());
        scaleFilter.addItemListener(this);
        options.add(scaleFilter, "cell 1 7,alignx left,aligny center");

        dateFilter = new JCheckBox("Date Filter");
        dateFilter.setSelected(mc.isDateFilter());
        dateFilter.addItemListener(this);
        options.add(dateFilter, "cell 0 9,alignx left,aligny center");

        soundings = new JCheckBox("Soundings");
        soundings.setSelected(mc.isSoundings());
        soundings.addItemListener(this);
        options.add(soundings, "cell 1 8,alignx left,aligny center");

        contourLabels = new JCheckBox("Contour Labels");
        contourLabels.setSelected(mc.isContourLabels());
        contourLabels.addItemListener(this);
        options.add(contourLabels, "cell 1 9,alignx left,aligny center");

        lowAccurateSymbols = new JCheckBox("Low Accurate Symbols");
        lowAccurateSymbols.setSelected(mc.isContourLabels());
        lowAccurateSymbols.addItemListener(this);
        options.add(lowAccurateSymbols, "cell 1 10,alignx left,aligny center");

        showIsolatedDangerInShallowWater = new JCheckBox("Show Isolated Danger in Shallow Water");
        showIsolatedDangerInShallowWater.setSelected(mc.isContourLabels());
        showIsolatedDangerInShallowWater.addItemListener(this);
        options.add(showIsolatedDangerInShallowWater, "cell 1 11,alignx left,aligny center");
    }

    @Override
    public void itemStateChanged(ItemEvent e) {
        Object source = e.getItemSelectable();
        if (e.getStateChange() == ItemEvent.DESELECTED) {
            if (source == scaleFilter) {
                mc.setScaleFilter(false);
            }
            if (source == dateFilter) {
                mc.setDateFilter(false);
            }
            if (source == showText) {
                mc.setShowImportantText(false);
            }
            if (source == soundings) {
                mc.setSoundings(false);
            }
            if (source == contourLabels) {
                mc.setContourLabels(false);
            }
            if (source == lowAccurateSymbols) {
                mc.setLowAccurateSymbols(false);
            }
            if (source == showIsolatedDangerInShallowWater) {
                mc.setShowIsolatedDangerInShallowWater(false);
            }
            if (source == otherText) {
                mc.setShowOtherText(false);
            }
            parent.refreshRenderer();
            return;
        }
        if (source == areaStyle) {
            if (((String) e.getItem()).equals(LookupTableType.SYMBOLIZED_BOUNDARIES.toString()))
                mc.setAreaStyle(LookupTableType.SYMBOLIZED_BOUNDARIES);
            if (((String) e.getItem()).equals(LookupTableType.PLAIN_BOUNDARIES.toString()))
                mc.setAreaStyle(LookupTableType.PLAIN_BOUNDARIES);

        }
        if (source == pointStyle) {
            if (((String) e.getItem()).equals("PAPER_CHART"))
                mc.setPointStyle(LookupTableType.PAPER_CHART);
            if (((String) e.getItem()).equals("SIMPLIFIED"))
                mc.setPointStyle(LookupTableType.SIMPLIFIED);
        }
        if (source == colorScheme) {
            mc.setColorScheme(ColorScheme.valueOf((String) e.getItem()));
        }
        if (source == displayCat) {
            if (((String) e.getItem()).equals("DISPLAYBASE"))
                mc.setDisplayCategory(DisplayCategory.DISPLAYBASE);
            if (((String) e.getItem()).equals("STANDARD"))
                mc.setDisplayCategory(DisplayCategory.STANDARD);
            if (((String) e.getItem()).equals("OTHER"))
                mc.setDisplayCategory(DisplayCategory.OTHER);
            if (((String) e.getItem()).equals("MARINERS_OTHER"))
                mc.setDisplayCategory(DisplayCategory.MARINERS_OTHER);
            if (((String) e.getItem()).equals("MARINERS_STANDARD"))
                mc.setDisplayCategory(DisplayCategory.MARINERS_STANDARD);
        }

        if (source == scaleFilter) {
            mc.setScaleFilter(true);
        }
        if (source == dateFilter) {
            mc.setDateFilter(true);
        }
        if (source == showText) {
            mc.setShowImportantText(true);
        }
        if (source == soundings) {
            mc.setSoundings(true);
        }
        if (source == contourLabels) {
            mc.setContourLabels(true);
        }
        if (source == lowAccurateSymbols) {
            mc.setLowAccurateSymbols(true);
        }
        if (source == showIsolatedDangerInShallowWater) {
            mc.setShowIsolatedDangerInShallowWater(true);
        }
        if (source == otherText) {
            mc.setShowOtherText(true);
        }

        parent.refreshRenderer();
    }

    public void refresh() {
        pointStyle.setSelectedItem(mc.getPointStyle().toString());
        areaStyle.setSelectedItem(mc.getAreaStyle().toString());
        colorScheme.setSelectedItem(mc.getColorScheme().toString());
        displayCat.setSelectedItem(mc.getDisplayCategory().toString());

        safety.setText(Double.toString(mc.getSafetyContour()));
        deep.setText(Double.toString(mc.getDeepContour()));
        shallow.setText(Double.toString(mc.getShallowContour()));

        showText.setSelected(mc.isShowImportantText());
        scaleFilter.setSelected(mc.isScaleFilter());
        dateFilter.setSelected(mc.isDateFilter());
        otherText.setSelected(mc.isShowOtherText());
        soundings.setSelected(mc.isSoundings());
        contourLabels.setSelected(mc.isContourLabels());
        lowAccurateSymbols.setSelected(mc.isLowAccurateSymbols());
        showIsolatedDangerInShallowWater.setSelected(mc.isShowIsolatedDangerInShallowWater());
    }
}
