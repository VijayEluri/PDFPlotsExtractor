/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package invenio.pdf.core;

import de.intarsys.pdf.content.CSOperation;

/**
 * A description of an operation that only changes the internal state
 * 
 * @author piotr
 */
public class TransformationOperation extends Operation{
    public TransformationOperation(CSOperation orig){
        super(orig);
    }
}
