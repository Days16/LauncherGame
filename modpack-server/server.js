const express = require('express');
const multer = require('multer');
const path = require('path');
const fs = require('fs-extra');
const cors = require('cors');
const AdmZip = require('adm-zip');
const os = require('os');

const app = express();
const PORT = 3000;

// New storage path in Documents
const BASE_DIR = path.join(os.homedir(), 'Documents', 'MinecraftLauncher');
const MODPACKS_DIR = path.join(BASE_DIR, 'server_modpacks');
const MANIFEST_PATH = path.join(BASE_DIR, 'server_modpacks.json');

fs.ensureDirSync(MODPACKS_DIR);

app.use(cors());
app.use(express.json());
app.use(express.static('public'));
// Serve modpacks from the new Documents path
app.use('/modpacks', express.static(MODPACKS_DIR));

const storage = multer.diskStorage({
    destination: (req, file, cb) => {
        fs.ensureDirSync(MODPACKS_DIR);
        cb(null, MODPACKS_DIR);
    },
    filename: (req, file, cb) => {
        cb(null, file.originalname);
    }
});

const upload = multer({ storage });

function getModpackMetadata(filePath) {
    try {
        const zip = new AdmZip(filePath);
        const zipEntries = zip.getEntries();

        // Check for Modrinth (mrpack)
        const modrinthEntry = zipEntries.find(e => e.entryName === 'modrinth.index.json');
        if (modrinthEntry) {
            const data = JSON.parse(modrinthEntry.getData().toString('utf8'));
            return {
                minecraftVersion: data.dependencies?.minecraft || '1.20.1',
                name: data.name || path.basename(filePath, path.extname(filePath))
            };
        }

        // Check for CurseForge (zip)
        const curseEntry = zipEntries.find(e => e.entryName === 'manifest.json');
        if (curseEntry) {
            const data = JSON.parse(curseEntry.getData().toString('utf8'));
            return {
                minecraftVersion: data.minecraft?.version || '1.20.1',
                name: data.name || path.basename(filePath, path.extname(filePath))
            };
        }
    } catch (err) {
        console.error('Error reading zip metadata:', err);
    }
    return {
        minecraftVersion: '1.20.1',
        name: path.basename(filePath, path.extname(filePath))
    };
}

function updateManifest() {
    fs.ensureDirSync(MODPACKS_DIR);

    const files = fs.readdirSync(MODPACKS_DIR);
    const modpacks = files.filter(f => f.endsWith('.zip') || f.endsWith('.mrpack')).map(f => {
        const filePath = path.join(MODPACKS_DIR, f);
        const metadata = getModpackMetadata(filePath);
        const id = f.replace(/\.(zip|mrpack)$/, '').replace(/\s+/g, '-');

        return {
            id: id,
            name: metadata.name,
            version: '1.0.0',
            minecraftVersion: metadata.minecraftVersion,
            filename: f,
            description: `Hosted modpack: ${metadata.name}`,
            downloadUrl: `http://localhost:${PORT}/modpacks/${encodeURIComponent(f)}`
        };
    });

    fs.writeJsonSync(MANIFEST_PATH, { modpacks }, { spaces: 2 });
}

app.post('/upload', upload.single('modpack'), (req, res) => {
    if (!req.file) {
        return res.status(400).send('No file uploaded.');
    }
    updateManifest();
    res.send({ message: 'File uploaded successfully', filename: req.file.filename });
});

app.get('/manifest', (req, res) => {
    if (fs.existsSync(MANIFEST_PATH)) {
        res.sendFile(path.resolve(MANIFEST_PATH));
    } else {
        res.json({ modpacks: [] });
    }
});

app.delete('/modpacks/:filename', (req, res) => {
    const filePath = path.join(MODPACKS_DIR, req.params.filename);
    if (fs.existsSync(filePath)) {
        fs.unlinkSync(filePath);
        updateManifest();
        res.send('File deleted.');
    } else {
        res.status(404).send('File not found.');
    }
});

app.listen(PORT, () => {
    console.log(`Server running at http://localhost:${PORT}`);
    console.log(`Storing modpacks in: ${MODPACKS_DIR}`);
    fs.ensureDirSync(MODPACKS_DIR);
    updateManifest();
});
