package com.sateda.keyonekb2;

import com.fasterxml.jackson.annotation.JsonProperty;



public class KeyVariants {

    @JsonProperty(index=10)
    public int KeyCode = 0;

    public int scan_code = 0;


    public int one_press = 0;

    @JsonProperty(index=20)
    public Character SinglePress = null;
    public int one_press_shift = 0;

    @JsonProperty(index=30)
    public Character SinglePressShiftMode = null;
    public int double_press = 0;

    @JsonProperty(index=40)
    public Character DoublePress = null;



    public int double_press_shift = 0;

    @JsonProperty(index=50)
    public Character DoublePressShiftMode = null;

    public int alt = 0;

    @JsonProperty(index=60)
    public Character SinglePressAltMode = null;

    public int alt_shift = 0;

    @JsonProperty(index=70)
    public Character SinglePressAltShiftMode = null;
    public String alt_popup = "";
    @JsonProperty(index=80)
    public String AltMoreVariants = null;
    public String alt_shift_popup = "";
    @JsonProperty(index=90)
    public String AltShiftMoreVariants = null;
}
