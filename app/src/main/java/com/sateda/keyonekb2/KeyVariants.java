package com.sateda.keyonekb2;

import com.fasterxml.jackson.annotation.JsonProperty;



public class KeyVariants {
    @JsonProperty(index=10)
    public int KeyCode = 0;
    @JsonProperty(index=20)
    public Character SinglePress = null;
    @JsonProperty(index=30)
    public Character SinglePressShiftMode = null;
    @JsonProperty(index=40)
    public Character DoublePress = null;
    @JsonProperty(index=50)
    public Character DoublePressShiftMode = null;
    @JsonProperty(index=60)
    public Character SinglePressAltMode = null;
    @JsonProperty(index=70)
    public Character SinglePressAltShiftMode = null;
    @JsonProperty(index=80)
    public String AltMoreVariants = null;
    @JsonProperty(index=90)
    public String AltShiftMoreVariants = null;
}
