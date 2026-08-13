package com.combat;

import java.lang.reflect.Method;

public class PrintMethods {
    public static void main(String[] args) {
        try {
            Class<?> clazz = Class.forName("net.minecraft.world.item.alchemy.PotionBrewing");
            System.out.println("Methods of PotionBrewing:");
            for (Method m : clazz.getDeclaredMethods()) {
                System.out.println("  " + m.getName() + " -> " + m.getReturnType().getSimpleName() + " with parameters:");
                for (Class<?> p : m.getParameterTypes()) {
                    System.out.println("    - " + p.getName());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
