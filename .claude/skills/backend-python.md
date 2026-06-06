# Skill : Backend Python — FastAPI + SQLite

## Contexte
Backend local MVP pour recevoir, stocker et exposer les audits Wi-Fi.
S'exécute sur le PC ou NAS du développeur, sur le même réseau Wi-Fi que le smartphone.

---

## Structure des fichiers

```
backend/
├── main.py         # Routes FastAPI
├── models.py       # Modèles SQLAlchemy
├── schemas.py      # Schémas Pydantic (validation)
├── database.py     # Connexion SQLite
├── plans/          # Images des plans (créé automatiquement)
└── wifi_audits.db  # Base SQLite (créée automatiquement)
```

---

## database.py

```python
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, DeclarativeBase

DATABASE_URL = "sqlite:///./wifi_audits.db"

engine = create_engine(
    DATABASE_URL,
    connect_args={"check_same_thread": False}
)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

class Base(DeclarativeBase):
    pass

def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
```

---

## models.py

```python
from sqlalchemy import Column, String, Float, Integer, DateTime, ForeignKey, JSON
from sqlalchemy.orm import relationship
from database import Base
import uuid, datetime

class Audit(Base):
    __tablename__ = "audits"

    id               = Column(String, primary_key=True, default=lambda: str(uuid.uuid4()))
    created_at       = Column(DateTime, default=datetime.datetime.utcnow)
    ssid             = Column(String, nullable=False)
    device_info      = Column(JSON)
    plan_image_path  = Column(String)
    gateway_pos      = Column(JSON)          # {"x": float, "y": float}
    repeater_pos     = Column(JSON)          # [{"id": str, "x": float, "y": float}]
    rooms            = Column(JSON)          # [{"id": str, "type": str, "label": str, "bounds": {...}}]
    summary          = Column(JSON)
    status           = Column(String, default="received")

    measurements = relationship(
        "Measurement", back_populates="audit", cascade="all, delete-orphan"
    )

class Measurement(Base):
    __tablename__ = "measurements"

    id               = Column(String, primary_key=True, default=lambda: str(uuid.uuid4()))
    audit_id         = Column(String, ForeignKey("audits.id"), nullable=False)
    room_id          = Column(String, nullable=True)   # ID de la CanvasRoom (peut être null)
    x                = Column(Float, nullable=False)
    y                = Column(Float, nullable=False)
    rssi             = Column(Integer, nullable=False)
    bssid            = Column(String)
    channel          = Column(Integer)
    band             = Column(String)
    ping_gateway_ms  = Column(Integer)
    ping_internet_ms = Column(Integer)
    neighbors        = Column(JSON)

    audit = relationship("Audit", back_populates="measurements")
```

---

## schemas.py

```python
from pydantic import BaseModel
from typing import Optional
from datetime import datetime

class Position(BaseModel):
    x: float
    y: float

class RepeaterPosition(BaseModel):
    id: str
    x: float
    y: float

class NeighborNetwork(BaseModel):
    ssid: Optional[str]
    bssid: str
    rssi: int
    channel: int
    band: str

class DeviceInfo(BaseModel):
    model: str
    android_version: str
    app_version: str

class RoomBounds(BaseModel):
    left: float
    top: float
    right: float
    bottom: float

class RoomCreate(BaseModel):
    id: str
    type: str         # "SALON" | "KITCHEN" | "BEDROOM" | "OFFICE" | "BATHROOM" | "HALLWAY" | "DINING" | "OTHER"
    label: str
    bounds: RoomBounds

class MeasurementCreate(BaseModel):
    x: float
    y: float
    room_id: Optional[str] = None    # ID de la CanvasRoom contenant ce point
    rssi: int
    bssid: str
    channel: int
    band: str
    ping_gateway_ms: int
    ping_internet_ms: int
    neighboring_networks: list[NeighborNetwork] = []

class AuditCreate(BaseModel):
    audit_id: str
    created_at: str
    ssid: str
    device_info: DeviceInfo
    plan_image_base64: Optional[str] = None
    rooms: list[RoomCreate] = []
    gateway_position: Position
    repeater_positions: list[RepeaterPosition] = []
    measurements: list[MeasurementCreate]

class AuditSummary(BaseModel):
    id: str
    created_at: datetime
    ssid: str
    measurement_count: int
    status: str

    class Config:
        from_attributes = True
```

---

## main.py

```python
from fastapi import FastAPI, Depends, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy.orm import Session
import base64, os

import models, schemas
from database import engine, get_db, Base

Base.metadata.create_all(bind=engine)

app = FastAPI(
    title="Wi-Fi Audit — Backend local",
    description="Stockage des audits Wi-Fi pour analyse et migration cloud future",
    version="1.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.post("/audits", status_code=201)
def create_audit(payload: schemas.AuditCreate, db: Session = Depends(get_db)):
    image_path = None
    if payload.plan_image_base64:
        os.makedirs("plans", exist_ok=True)
        image_path = f"plans/{payload.audit_id}.jpg"
        with open(image_path, "wb") as f:
            f.write(base64.b64decode(payload.plan_image_base64))

    audit = models.Audit(
        id=payload.audit_id,
        ssid=payload.ssid,
        device_info=payload.device_info.model_dump(),
        plan_image_path=image_path,
        gateway_pos=payload.gateway_position.model_dump(),
        repeater_pos=[r.model_dump() for r in payload.repeater_positions],
        rooms=[r.model_dump() for r in payload.rooms],   # liste de CanvasRoom sérialisées
    )
    db.add(audit)

    for m in payload.measurements:
        db.add(models.Measurement(
            audit_id=audit.id,
            room_id=m.room_id,
            x=m.x, y=m.y,
            rssi=m.rssi, bssid=m.bssid,
            channel=m.channel, band=m.band,
            ping_gateway_ms=m.ping_gateway_ms,
            ping_internet_ms=m.ping_internet_ms,
            neighbors=[n.model_dump() for n in m.neighboring_networks],
        ))

    db.commit()
    return {"id": audit.id, "measurement_count": len(payload.measurements)}


@app.get("/audits", response_model=list[schemas.AuditSummary])
def list_audits(db: Session = Depends(get_db)):
    audits = db.query(models.Audit).order_by(models.Audit.created_at.desc()).all()
    return [
        {**a.__dict__, "measurement_count": len(a.measurements)}
        for a in audits
    ]


@app.get("/audits/{audit_id}")
def get_audit(audit_id: str, db: Session = Depends(get_db)):
    audit = db.query(models.Audit).filter(models.Audit.id == audit_id).first()
    if not audit:
        raise HTTPException(status_code=404, detail="Audit introuvable")
    return {
        **audit.__dict__,
        "measurements": [m.__dict__ for m in audit.measurements]
    }


@app.delete("/audits/{audit_id}", status_code=204)
def delete_audit(audit_id: str, db: Session = Depends(get_db)):
    audit = db.query(models.Audit).filter(models.Audit.id == audit_id).first()
    if not audit:
        raise HTTPException(status_code=404, detail="Audit introuvable")
    db.delete(audit)
    db.commit()


@app.get("/health")
def health():
    return {"status": "ok"}
```

---

## Lancement

```bash
cd backend
pip install fastapi uvicorn sqlalchemy pillow python-multipart
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

- Swagger UI : `http://localhost:8000/docs`
- Trouver l'IP locale : `ipconfig` (Windows) / `ifconfig` (Mac/Linux)
- Saisir cette IP dans l'app Android : `http://192.168.x.x:8000`

---

## Analyse rapide des données (script Python)

```python
import sqlite3, json

conn = sqlite3.connect("wifi_audits.db")
conn.row_factory = sqlite3.Row

audits = conn.execute(
    "SELECT id, created_at, ssid FROM audits ORDER BY created_at DESC"
).fetchall()

for a in audits:
    meas = conn.execute(
        "SELECT rssi, ping_gateway_ms, ping_internet_ms FROM measurements WHERE audit_id = ?",
        (a["id"],)
    ).fetchall()
    if not meas:
        continue
    avg_rssi = sum(m["rssi"] for m in meas) / len(meas)
    avg_ping = sum(m["ping_gateway_ms"] for m in meas if m["ping_gateway_ms"]) / len(meas)
    print(f"{a['created_at']} | {a['ssid']:20s} | {len(meas)} pts | RSSI moy: {avg_rssi:.1f} dBm | ping GW: {avg_ping:.0f}ms")
```

---

## Migration cloud (futur)

Remplacer dans `database.py` :
```python
# SQLite local
DATABASE_URL = "sqlite:///./wifi_audits.db"

# PostgreSQL cloud (Railway / Render / Supabase)
DATABASE_URL = os.environ["DATABASE_URL"]
engine = create_engine(DATABASE_URL)  # supprimer connect_args
```

Aucune autre modification nécessaire. L'app Android ne change pas (même URL de contrat).

---

## Règles de code Python
- Pydantic v2 (`model_dump()` et non `dict()`)
- SQLAlchemy 2.0 (style déclaratif avec `DeclarativeBase`)
- Toujours utiliser `Depends(get_db)` pour les sessions — jamais de session globale
- Les routes retournent toujours des types sérialisables (dict ou modèles Pydantic)
- Pas de logique métier dans `main.py` — extraire dans des fonctions séparées si complexe
