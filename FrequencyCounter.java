/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package svostats;

/**
 *
 * @author wbolduc
 */
public class FrequencyCounter <T>{
    public final T thing;
    public final int count;

    public FrequencyCounter(T thing, int count) {
        this.thing = thing;
        this.count = count;
    }
}
