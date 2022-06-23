package com.sateda.keyonekb2;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collection;
import java.util.HashMap;


public class KeyboardLayout {

    @JsonProperty(index=10)
    public String KeyboardName = "";

    @JsonProperty(index=20)
    public String AltModeLayout = "";

    @JsonProperty(index=30)
    public String SymModeLayout = "";
    public int SymXmlId = 0;

    public KeyboardLayoutRes Resources;

    @JsonProperty(index=40)
    public Collection<KeyVariants> KeyMapping;

}
