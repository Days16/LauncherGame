const express = require('express');
const multer = require('multer');
const path = require('path');
const fs = require('fs-extra');
const cors = require('cors');
const AdmZip = require('adm-zip');
const os = require('os');

const app = express();
const PORT = process.env.PORT || 3000;

// Vercel compatibility: Use /tmp for storage if running in serverless environment
const isVercel = process.env.VERCEL === '1';
const BASE_DIR = isVercel
    ? '/tmp'
    : path.join(os.homedir(), 'Documents', 'MinecraftLauncher');

const MODPACKS_DIR = path.join(BASE_DIR, 'server_modpacks');
const MANIFEST_PATH = path.join(BASE_DIR, 'server_modpacks.json');

// Ensure directories exist
if (!isVercel) {
    fs.ensureDirSync(MODPACKS_DIR);
}

app.use(cors());
app.use(express.json());
app.use(express.static('public'));
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

        const modrinthEntry = zipEntries.find(e => e.entryName === 'modrinth.index.json');
        if (modrinthEntry) {
            const data = JSON.parse(modrinthEntry.getData().toString('utf8'));
            return {
                minecraftVersion: data.dependencies?.minecraft || '1.20.1',
                name: data.name || path.basename(filePath, path.extname(filePath))
            };
        }

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

function getManifest(req) {
    if (!fs.existsSync(MODPACKS_DIR)) return { modpacks: [] };

    const protocol = req.protocol;
    const host = req.get('host');
    const baseUrl = `${protocol}://${host}`;

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
            downloadUrl: `${baseUrl}/modpacks/${encodeURIComponent(f)}`
        };
    });

    return { modpacks };
}

app.post('/upload', upload.single('modpack'), (req, res) => {
    if (!req.file) {
        return res.status(400).send('No file uploaded.');
    }
    // On Vercel, we don't persist the manifest file, we generate it on the fly
    res.send({ message: 'File uploaded successfully', filename: req.file.filename });
});

app.get('/manifest', (req, res) => {
    const manifest = getManifest(req);
    res.json(manifest);
});

app.get('/modpacks.json', (req, res) => {
    const manifest = getManifest(req);
    res.json(manifest);
});

app.delete('/modpacks/:filename', (req, res) => {
    const filePath = path.join(MODPACKS_DIR, req.params.filename);
    if (fs.existsSync(filePath)) {
        fs.unlinkSync(filePath);
        res.send('File deleted.');
    } else {
        res.status(404).send('File not found.');
    }
});

// For local development
if (!process.env.VERCEL) {
    app.listen(PORT, () => {
        console.log(`Server running at http://localhost:${PORT}`);
        console.log(`Storing modpacks in: ${MODPACKS_DIR}`);
        fs.ensureDirSync(MODPACKS_DIR);
    });
}

// Export for Vercel
module.exports = app;
