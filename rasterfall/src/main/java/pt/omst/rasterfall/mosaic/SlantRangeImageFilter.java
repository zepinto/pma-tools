/*
 * Copyright (c) 2004-2016 Universidade do Porto - Faculdade de Engenharia
 * Laboratório de Sistemas e Tecnologia Subaquática (LSTS)
 * All rights reserved.
 * Rua Dr. Roberto Frias s/n, sala I203, 4200-465 Porto, Portugal
 *
 * This file is part of Neptus, Command and Control Framework.
 *
 * Commercial Licence Usage
 * Licencees holding valid commercial Neptus licences may use this file
 * in accordance with the commercial licence agreement provided with the
 * Software or, alternatively, in accordance with the terms contained in a
 * written agreement between you and Universidade do Porto. For licensing
 * terms, conditions, and further information contact lsts@fe.up.pt.
 *
 * European Union Public Licence - EUPL v.1.1 Usage
 * Alternatively, this file may be used under the terms of the EUPL,
 * Version 1.1 only (the "Licence"), appearing in the file LICENCE.md
 * included in the packaging of this file. You may not use this work
 * except in compliance with the Licence. Unless required by applicable
 * law or agreed to in writing, software distributed under the Licence is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific
 * language governing permissions and limitations at
 * http://ec.europa.eu/idabc/eupl.html.
 *
 * For more information please see <http://lsts.fe.up.pt/neptus>.
 *
 * Author: José Pinto
 * Dec 19, 2012
 */

package pt.omst.rasterfall.mosaic;

import com.jhlabs.image.TransformFilter;

/**
 * @author zp
 */
public class SlantRangeImageFilter extends TransformFilter {

    protected double height, range;
    protected double imgWidth;

    public SlantRangeImageFilter(double height, double range, int imgWidth) {
        this.imgWidth = imgWidth;
        this.height = height;
        this.range = range;

    }

    @Override
    protected void transformInverse(int x, int y, float[] out) {

        // r*r = x*x + h*h <=> r = sqrt(x*x+h*h)

        double h = height * (imgWidth / (range * 2));
        double d = Math.abs(imgWidth / 2 - x);

        out[1] = y;
        if (x < imgWidth / 2)
            out[0] = (float) (imgWidth / 2 - Math.sqrt(d * d + h * h));
        else
            out[0] = (float) (imgWidth / 2 + Math.sqrt(d * d + h * h));
    }

    public void setHeight(double height) {
        this.height = height * 2;
    }
}
