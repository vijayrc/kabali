package com.vijayrc.kabali

/**
 * Created by vijayrc on 11/11/16.
 */
class CreditProfile extends Samplable {

  override def getJson: String = {
    "return the json { } payload containing all the credit card balances and history. This will become a dataset"
  }

  override def getKey: String = {
    "return the the unique entity key like SSN" //implement to give a entity key
  }
}
