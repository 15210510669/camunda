package org.camunda.optimize.dto.optimize.importing.index;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public class TimestampBasedImportIndexDto implements ImportIndexDto {

  protected OffsetDateTime timestampOfLastEntity = OffsetDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
  protected String esTypeIndexRefersTo;
  protected String engine;

  public String getEngine() {
    return engine;
  }

  public void setEngine(String engine) {
    this.engine = engine;
  }

  public String getEsTypeIndexRefersTo() {
    return esTypeIndexRefersTo;
  }

  public void setEsTypeIndexRefersTo(String esTypeIndexRefersTo) {
    this.esTypeIndexRefersTo = esTypeIndexRefersTo;
  }

  public OffsetDateTime getTimestampOfLastEntity() {
    return timestampOfLastEntity;
  }

  public void setTimestampOfLastEntity(OffsetDateTime timestampOfLastEntity) {
    this.timestampOfLastEntity = timestampOfLastEntity;
  }
}
