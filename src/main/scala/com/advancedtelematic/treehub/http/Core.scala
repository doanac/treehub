/**
  * Copyright: Copyright (C) 2016, ATS Advanced Telematic Systems GmbH
  * License: MPL-2.0
  */
package com.advancedtelematic.treehub.http

import com.advancedtelematic.data.DataType.Ref

import scala.concurrent.{ExecutionContext, Future}

trait Core {
  def storeCommitInCore(ref: Ref, description: String)(implicit ec: ExecutionContext): Future[Unit]
}