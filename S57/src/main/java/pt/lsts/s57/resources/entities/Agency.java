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
 * 
 */
package pt.lsts.s57.resources.entities;

import java.io.Serializable;

public class Agency implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 3200893342636442831L;
    protected final Integer agencyID;
    protected final String token;
    protected final Integer code;
    protected final String agencyName;
    protected final String country;

    protected Agency(Integer agencyID, String token, Integer code, String agencyName, String country) {
        this.agencyID = agencyID;
        this.token = token;
        this.code = code;
        this.agencyName = agencyName;
        this.country = country;
    }

    /**
     * @return the agencyID
     */
    public Integer getAgencyID() {
        return agencyID;
    }

    /**
     * @return the token
     */
    public String getToken() {
        return token;
    }

    /**
     * @return the code
     */
    public Integer getCode() {
        return code;
    }

    /**
     * @return the agencyName
     */
    public String getAgencyName() {
        return agencyName;
    }

    /**
     * @return the country
     */
    public String getCountry() {
        return country;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "Agency [agencyID=" + agencyID + ", token=" + token + ", code=" + code + ", agencyName=" + agencyName
                + ", country=" + country + "]";
    }

}
