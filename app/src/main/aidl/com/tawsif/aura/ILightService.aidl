package com.tawsif.aura;

interface ILightService {
    void destroy() = 16777114;
    String call(String op, String pkg, String arg, int a, int b) = 1;
}
