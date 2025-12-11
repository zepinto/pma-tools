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
 * Oct 26, 2011
 */
package pt.lsts.s57.mc;

import java.util.Objects;

import pt.lsts.s57.resources.entities.s52.ColorScheme;
import pt.lsts.s57.resources.entities.s52.DisplayCategory;
import pt.lsts.s57.resources.entities.s52.LookupTableType;

/**
 * Mariner Controls configuration for S57 map display. Uses Builder pattern for flexible construction of immutable-like
 * instances.
 * 
 * @author Hugo Dias
 */
public final class MarinerControls implements Cloneable {

    private LookupTableType pointStyle;
    private LookupTableType areaStyle;
    private ColorScheme colorScheme;
    private DisplayCategory displayCategory;
    private double shallowContour;
    private double deepContour;
    private double safetyContour;
    private boolean twoShades;
    private boolean shallowPattern;
    private boolean showImportantText;
    private boolean showOtherText;
    private boolean soundings;
    private boolean contourLabels;
    private boolean lowAccurateSymbols;
    private boolean showIsolatedDangerInShallowWater;
    private double mapScale;
    private boolean scaleFilter;
    private boolean dateFilter;

    /**
     * Creates a new MarinerControls with default values.
     * 
     * @return new MarinerControls instance with defaults
     */
    public static MarinerControls forge() {
        return new MarinerControls();
    }

    /**
     * Creates a new Builder for constructing MarinerControls.
     * 
     * @return new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    private MarinerControls() {
        this.pointStyle = LookupTableType.SIMPLIFIED;
        this.areaStyle = LookupTableType.PLAIN_BOUNDARIES;
        this.colorScheme = ColorScheme.DAY_BRIGHT;
        this.displayCategory = DisplayCategory.OTHER;
        this.shallowContour = 3.0;
        this.deepContour = 10.0;
        this.safetyContour = 8.0;
        this.twoShades = false;
        this.shallowPattern = false;
        this.showImportantText = false;
        this.showOtherText = false;
        this.scaleFilter = true;
        this.dateFilter = true;
        this.soundings = true;
        this.contourLabels = false;
        this.lowAccurateSymbols = false;
        this.showIsolatedDangerInShallowWater = false;
    }

    private MarinerControls(Builder builder) {
        this.pointStyle = builder.pointStyle;
        this.areaStyle = builder.areaStyle;
        this.colorScheme = builder.colorScheme;
        this.displayCategory = builder.displayCategory;
        this.shallowContour = builder.shallowContour;
        this.deepContour = builder.deepContour;
        this.safetyContour = builder.safetyContour;
        this.twoShades = builder.twoShades;
        this.shallowPattern = builder.shallowPattern;
        this.showImportantText = builder.showImportantText;
        this.showOtherText = builder.showOtherText;
        this.scaleFilter = builder.scaleFilter;
        this.dateFilter = builder.dateFilter;
        this.soundings = builder.soundings;
        this.contourLabels = builder.contourLabels;
        this.lowAccurateSymbols = builder.lowAccurateSymbols;
        this.showIsolatedDangerInShallowWater = builder.showIsolatedDangerInShallowWater;
        this.mapScale = builder.mapScale;
    }

    /**
     * Builder for constructing MarinerControls instances.
     */
    public static final class Builder {
        private LookupTableType pointStyle = LookupTableType.SIMPLIFIED;
        private LookupTableType areaStyle = LookupTableType.PLAIN_BOUNDARIES;
        private ColorScheme colorScheme = ColorScheme.DAY_BRIGHT;
        private DisplayCategory displayCategory = DisplayCategory.OTHER;
        private double shallowContour = 3.0;
        private double deepContour = 10.0;
        private double safetyContour = 8.0;
        private boolean twoShades = false;
        private boolean shallowPattern = false;
        private boolean showImportantText = false;
        private boolean showOtherText = false;
        private boolean soundings = true;
        private boolean contourLabels = false;
        private boolean lowAccurateSymbols = false;
        private boolean showIsolatedDangerInShallowWater = false;
        private double mapScale = 0;
        private boolean scaleFilter = true;
        private boolean dateFilter = true;

        private Builder() {
        }

        public Builder pointStyle(LookupTableType pointStyle) {
            if (pointStyle != LookupTableType.PAPER_CHART && pointStyle != LookupTableType.SIMPLIFIED) {
                throw new IllegalArgumentException("Point style must be PAPER_CHART or SIMPLIFIED");
            }
            this.pointStyle = pointStyle;
            return this;
        }

        public Builder areaStyle(LookupTableType areaStyle) {
            if (areaStyle != LookupTableType.PLAIN_BOUNDARIES && areaStyle != LookupTableType.SYMBOLIZED_BOUNDARIES) {
                throw new IllegalArgumentException("Area style must be PLAIN_BOUNDARIES or SYMBOLIZED_BOUNDARIES");
            }
            this.areaStyle = areaStyle;
            return this;
        }

        public Builder colorScheme(ColorScheme colorScheme) {
            this.colorScheme = Objects.requireNonNull(colorScheme);
            return this;
        }

        public Builder displayCategory(DisplayCategory displayCategory) {
            this.displayCategory = Objects.requireNonNull(displayCategory);
            return this;
        }

        public Builder shallowContour(double shallowContour) {
            this.shallowContour = shallowContour;
            return this;
        }

        public Builder deepContour(double deepContour) {
            this.deepContour = deepContour;
            return this;
        }

        public Builder safetyContour(double safetyContour) {
            this.safetyContour = safetyContour;
            return this;
        }

        public Builder twoShades(boolean twoShades) {
            this.twoShades = twoShades;
            return this;
        }

        public Builder shallowPattern(boolean shallowPattern) {
            this.shallowPattern = shallowPattern;
            return this;
        }

        public Builder showImportantText(boolean showImportantText) {
            this.showImportantText = showImportantText;
            return this;
        }

        public Builder showOtherText(boolean showOtherText) {
            this.showOtherText = showOtherText;
            return this;
        }

        public Builder soundings(boolean soundings) {
            this.soundings = soundings;
            return this;
        }

        public Builder contourLabels(boolean contourLabels) {
            this.contourLabels = contourLabels;
            return this;
        }

        public Builder lowAccurateSymbols(boolean lowAccurateSymbols) {
            this.lowAccurateSymbols = lowAccurateSymbols;
            return this;
        }

        public Builder showIsolatedDangerInShallowWater(boolean show) {
            this.showIsolatedDangerInShallowWater = show;
            return this;
        }

        public Builder mapScale(double mapScale) {
            this.mapScale = mapScale;
            return this;
        }

        public Builder scaleFilter(boolean scaleFilter) {
            this.scaleFilter = scaleFilter;
            return this;
        }

        public Builder dateFilter(boolean dateFilter) {
            this.dateFilter = dateFilter;
            return this;
        }

        public MarinerControls build() {
            return new MarinerControls(this);
        }
    }

    // Getters

    public LookupTableType getPointStyle() {
        return pointStyle;
    }

    public LookupTableType getAreaStyle() {
        return areaStyle;
    }

    public ColorScheme getColorScheme() {
        return colorScheme;
    }

    public DisplayCategory getDisplayCategory() {
        return displayCategory;
    }

    public double getShallowContour() {
        return shallowContour;
    }

    public double getDeepContour() {
        return deepContour;
    }

    public double getSafetyContour() {
        return safetyContour;
    }

    public boolean isTwoShades() {
        return twoShades;
    }

    public boolean isShallowPattern() {
        return shallowPattern;
    }

    public boolean isShowImportantText() {
        return showImportantText;
    }

    public boolean isShowOtherText() {
        return showOtherText;
    }

    public boolean isSoundings() {
        return soundings;
    }

    public boolean isContourLabels() {
        return contourLabels;
    }

    public boolean isLowAccurateSymbols() {
        return lowAccurateSymbols;
    }

    public boolean isShowIsolatedDangerInShallowWater() {
        return showIsolatedDangerInShallowWater;
    }

    public double getMapScale() {
        return mapScale;
    }

    public boolean isScaleFilter() {
        return scaleFilter;
    }

    public boolean isDateFilter() {
        return dateFilter;
    }

    // Setters (for backward compatibility)

    public void setPointStyle(LookupTableType pointStyle) {
        if (pointStyle != LookupTableType.PAPER_CHART && pointStyle != LookupTableType.SIMPLIFIED) {
            throw new IllegalArgumentException("Point style must be PAPER_CHART or SIMPLIFIED");
        }
        this.pointStyle = pointStyle;
    }

    public void setAreaStyle(LookupTableType areaStyle) {
        if (areaStyle != LookupTableType.PLAIN_BOUNDARIES && areaStyle != LookupTableType.SYMBOLIZED_BOUNDARIES) {
            throw new IllegalArgumentException("Area style must be PLAIN_BOUNDARIES or SYMBOLIZED_BOUNDARIES");
        }
        this.areaStyle = areaStyle;
    }

    public void setColorScheme(ColorScheme colorScheme) {
        this.colorScheme = colorScheme;
    }

    public void setDisplayCategory(DisplayCategory displayCategory) {
        this.displayCategory = displayCategory;
    }

    public void setShallowContour(double shallowContour) {
        this.shallowContour = shallowContour;
    }

    public void setDeepContour(double deepContour) {
        this.deepContour = deepContour;
    }

    public void setSafetyContour(double safetyContour) {
        this.safetyContour = safetyContour;
    }

    public void setTwoShades(boolean twoShades) {
        this.twoShades = twoShades;
    }

    public void setShallowPattern(boolean shallowPattern) {
        this.shallowPattern = shallowPattern;
    }

    public void setShowImportantText(boolean showImportantText) {
        this.showImportantText = showImportantText;
    }

    public void setShowOtherText(boolean showOtherText) {
        this.showOtherText = showOtherText;
    }

    public void setSoundings(boolean soundings) {
        this.soundings = soundings;
    }

    public void setContourLabels(boolean contourLabels) {
        this.contourLabels = contourLabels;
    }

    public void setLowAccurateSymbols(boolean lowAccurateSymbols) {
        this.lowAccurateSymbols = lowAccurateSymbols;
    }

    public void setShowIsolatedDangerInShallowWater(boolean showIsolatedDangerInShallowWater) {
        this.showIsolatedDangerInShallowWater = showIsolatedDangerInShallowWater;
    }

    public void setMapScale(double mapScale) {
        this.mapScale = mapScale;
    }

    public void setScaleFilter(boolean scaleFilter) {
        this.scaleFilter = scaleFilter;
    }

    public void setDateFilter(boolean dateFilter) {
        this.dateFilter = dateFilter;
    }

    @Override
    public MarinerControls clone() {
        try {
            return (MarinerControls) super.clone();
        }
        catch (CloneNotSupportedException e) {
            throw new AssertionError("Cloneable interface is implemented", e);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(areaStyle, colorScheme, deepContour, displayCategory, mapScale, pointStyle, safetyContour,
                scaleFilter, dateFilter, shallowContour, shallowPattern, showImportantText, showOtherText, soundings,
                contourLabels, lowAccurateSymbols, showIsolatedDangerInShallowWater, twoShades);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof MarinerControls other))
            return false;

        return areaStyle == other.areaStyle && colorScheme == other.colorScheme
                && Double.compare(deepContour, other.deepContour) == 0 && displayCategory == other.displayCategory
                && Double.compare(mapScale, other.mapScale) == 0 && pointStyle == other.pointStyle
                && Double.compare(safetyContour, other.safetyContour) == 0 && scaleFilter == other.scaleFilter
                && dateFilter == other.dateFilter && Double.compare(shallowContour, other.shallowContour) == 0
                && shallowPattern == other.shallowPattern && showImportantText == other.showImportantText
                && showOtherText == other.showOtherText && soundings == other.soundings
                && contourLabels == other.contourLabels && lowAccurateSymbols == other.lowAccurateSymbols
                && showIsolatedDangerInShallowWater == other.showIsolatedDangerInShallowWater
                && twoShades == other.twoShades;
    }
}
